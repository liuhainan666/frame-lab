package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.framelab.task.domain.FailureCode;
import com.framelab.task.domain.TaskStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class TaskPipelineApiTest extends TaskTestSupport {

    @Test
    void runsThreeStepsAndProducesReadableResult() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 140, 100, new Color(25, 70, 150));
        String second = uploadJpeg(login, "travel-postcard", 100, 140, new Color(150, 70, 25));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        zeroSimulationDelays();

        MvcResult notReady = mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(problemCode(notReady)).isEqualTo("TASK_NOT_READY");

        runQueuedTasks();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE id = ?", String.class, taskId)).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task_step WHERE task_id = ? AND seq = 1", String.class, taskId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task_step WHERE task_id = ? AND seq = 2", String.class, taskId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task_step WHERE task_id = ? AND seq = 3", String.class, taskId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id = ?", Integer.class, taskId))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id = ? AND status = 'SUCCEEDED'",
                Integer.class, taskId)).isEqualTo(3);

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT result_storage_key FROM task WHERE id = ?", String.class, taskId);
        long resultSize = jdbcTemplate.queryForObject(
                "SELECT result_size_bytes FROM task WHERE id = ?", Long.class, taskId);
        assertThat(storageKey).isEqualTo("results/1/" + taskId + ".jpg");
        Path stored = STORAGE_ROOT.resolve(storageKey);
        assertThat(stored).exists();
        assertThat(Files.size(stored)).isEqualTo(resultSize);
        Path tempDirectory = STORAGE_ROOT.resolve("tmp/task-" + taskId);
        if (Files.exists(tempDirectory)) {
            try (var files = Files.list(tempDirectory)) {
                assertThat(files.toList()).isEmpty();
            }
        }

        MvcResult result = mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"" + taskId + "-result\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("private")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("framelab-" + taskId.substring(0, 8) + ".jpg")))
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isEqualTo((int) resultSize);
        assertThat(bytes[0]).isEqualTo((byte) 0xFF);
        assertThat(bytes[1]).isEqualTo((byte) 0xD8);
        var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertThat(image.getWidth()).isEqualTo(1600);
        assertThat(image.getHeight()).isEqualTo(1200);

        mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .header(HttpHeaders.IF_NONE_MATCH, "W/\"" + taskId + "-result\""))
                .andExpect(status().isNotModified());

        JsonNode detail = taskDetail(login, taskId, "zh-CN");
        assertThat(detail.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(detail.path("currentStage").asText()).isEqualTo("FINALIZE");
        assertThat(detail.path("finishedAt").isNull()).isFalse();
        assertThat(detail.path("result").path("sizeBytes").asLong()).isEqualTo(resultSize);
        assertThat(detail.path("retry").path("attemptCount").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(TaskStage.class)
    void failsAtStagePermanentlyAndSkipsRemainingSteps(TaskStage stage) throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 120, 80, new Color(70, 120, 40));
        String second = uploadJpeg(login, "travel-postcard", 80, 120, new Color(40, 120, 70));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        zeroSimulationDelays();
        jdbcTemplate.update("""
                UPDATE simulation_setting
                   SET fail_stage = ?, fail_times = 1, fail_retryable = 0
                 WHERE id = 1
                """, stage.name());

        runQueuedTasks();

        String expectedCode = FailureCode.forStage(stage).name();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE id = ?", String.class, taskId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_code FROM task WHERE id = ?", String.class, taskId)).isEqualTo(expectedCode);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_simulated FROM task WHERE id = ?", Boolean.class, taskId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_stage FROM task WHERE id = ?", String.class, taskId)).isEqualTo(stage.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT finished_at FROM task WHERE id = ?", LocalDateTime.class, taskId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error_code FROM task WHERE id = ?", String.class, taskId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM task WHERE id = ?", LocalDateTime.class, taskId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fail_times FROM simulation_setting WHERE id = 1", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fail_stage FROM simulation_setting WHERE id = 1", String.class)).isNull();

        int failedSeq = stage.seq();
        for (int seq = 1; seq <= 3; seq++) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM task_step WHERE task_id = ? AND seq = ?", String.class, taskId, seq);
            if (seq < failedSeq) {
                assertThat(status).isEqualTo("SUCCEEDED");
            } else if (seq == failedSeq) {
                assertThat(status).isEqualTo("FAILED");
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM task_step WHERE task_id = ? AND seq = ?",
                        String.class, taskId, seq)).isEqualTo(expectedCode);
            } else {
                assertThat(status).isEqualTo("SKIPPED");
            }
            int attempts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM task_step_attempt a JOIN task_step s ON s.id = a.step_id"
                            + " WHERE a.task_id = ? AND s.seq = ?", Integer.class, taskId, seq);
            assertThat(attempts).isEqualTo(seq <= failedSeq ? 1 : 0);
        }

        JsonNode zh = taskDetail(login, taskId, "zh-CN");
        assertThat(zh.path("status").asText()).isEqualTo("FAILED");
        assertThat(zh.path("failure").path("stage").asText()).isEqualTo(stage.name());
        assertThat(zh.path("failure").path("code").asText()).isEqualTo(expectedCode);
        assertThat(zh.path("failure").path("simulated").asBoolean()).isTrue();
        assertThat(zh.path("failure").path("message").asText()).contains("模拟失败");
        assertThat(zh.path("result").isNull()).isTrue();
        assertThat(zh.path("steps").get(failedSeq - 1).path("message").asText()).contains("模拟失败");
        for (int seq = failedSeq + 1; seq <= 3; seq++) {
            assertThat(zh.path("steps").get(seq - 1).path("status").asText()).isEqualTo("SKIPPED");
            assertThat(zh.path("steps").get(seq - 1).path("message").isNull()).isTrue();
        }

        JsonNode en = taskDetail(login, taskId, "en-US");
        assertThat(en.path("failure").path("message").asText()).startsWith("Simulated failure:");

        MvcResult notReady = mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(problemCode(notReady)).isEqualTo("TASK_NOT_READY");

        LocalDateTime taskUpdated = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM task WHERE id = ?", LocalDateTime.class, taskId);
        List<String> stepStatuses = jdbcTemplate.queryForList(
                "SELECT status FROM task_step WHERE task_id = ? ORDER BY seq", String.class, taskId);
        int attempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id = ?", Integer.class, taskId);
        for (int index = 0; index < 3; index++) {
            taskDetail(login, taskId, "zh-CN");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM task WHERE id = ?", LocalDateTime.class, taskId))
                .isEqualTo(taskUpdated);
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM task_step WHERE task_id = ? ORDER BY seq", String.class, taskId))
                .isEqualTo(stepStatuses);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id = ?", Integer.class, taskId))
                .isEqualTo(attempts);
    }
}
