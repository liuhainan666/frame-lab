package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TaskRetryApiTest extends TaskTestSupport {

    @Test
    void retriesRetryableFailureThenSucceeds() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 100, 70, new Color(20, 90, 150));
        String second = uploadJpeg(login, "travel-postcard", 70, 100, new Color(150, 90, 20));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        injectFailure("GENERATE", 1, true);

        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(1);
        assertThat(taskField(taskId, "current_stage")).isEqualTo("GENERATE");
        assertThat(taskField(taskId, "last_error_code")).isEqualTo("GENERATION_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error_simulated FROM task WHERE id = ?", Boolean.class, taskId)).isTrue();
        assertThat(taskField(taskId, "failure_code")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT finished_at FROM task WHERE id = ?", LocalDateTime.class, taskId)).isNull();
        LocalDateTime nextRetry = jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM task WHERE id = ?", LocalDateTime.class, taskId);
        assertThat(nextRetry).isNotNull();
        assertThat(Duration.between(LocalDateTime.now(ZoneOffset.UTC), nextRetry).toSeconds())
                .isBetween(0L, 4L);

        assertThat(stepField(taskId, 1, "status")).isEqualTo("SUCCEEDED");
        assertThat(stepField(taskId, 2, "status")).isEqualTo("FAILED");
        assertThat(stepField(taskId, 2, "error_code")).isEqualTo("GENERATION_FAILED");
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(1);
        assertThat(stepField(taskId, 3, "status")).isEqualTo("PENDING");
        assertThat(attemptCount(taskId, 2)).isEqualTo(1);
        assertThat(attemptStatus(taskId, 2, 1)).isEqualTo("FAILED");

        LocalDateTime step1Started = stepTime(taskId, 1, "started_at");
        LocalDateTime step1Finished = stepTime(taskId, 1, "finished_at");
        String step1Args = stepField(taskId, 1, "message_args");

        JsonNode retrying = taskDetail(login, taskId, "zh-CN");
        assertThat(retrying.path("status").asText()).isEqualTo("RETRYING");
        assertThat(retrying.path("failure").isNull()).isTrue();
        assertThat(retrying.path("result").isNull()).isTrue();
        assertThat(retrying.path("retry").path("retryCount").asInt()).isEqualTo(1);
        assertThat(retrying.path("retry").path("attemptCount").asInt()).isEqualTo(1);
        assertThat(retrying.path("retry").path("maxAttempts").asInt()).isEqualTo(3);
        assertThat(retrying.path("retry").path("nextRetryAt").isNull()).isFalse();
        assertThat(retrying.path("retry").path("lastError").path("code").asText())
                .isEqualTo("GENERATION_FAILED");
        assertThat(retrying.path("retry").path("lastError").path("simulated").asBoolean()).isTrue();
        assertThat(retrying.path("retry").path("lastError").path("message").asText())
                .contains("模拟失败");
        assertThat(retrying.path("steps").get(1).path("message").asText()).contains("模拟失败");
        assertThat(retrying.path("steps").get(2).path("message").isNull()).isTrue();

        assertThat(taskClaimService.claimNext()).isNull();

        makeRetryDue(taskId);
        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("SUCCEEDED");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(1);
        assertThat(taskField(taskId, "last_error_code")).isNull();
        assertThat(taskField(taskId, "failure_code")).isNull();
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(2);
        assertThat(attemptCount(taskId, 2)).isEqualTo(2);
        assertThat(attemptStatus(taskId, 2, 1)).isEqualTo("FAILED");
        assertThat(attemptStatus(taskId, 2, 2)).isEqualTo("SUCCEEDED");
        assertThat(stepTime(taskId, 1, "started_at")).isEqualTo(step1Started);
        assertThat(stepTime(taskId, 1, "finished_at")).isEqualTo(step1Finished);
        assertThat(stepField(taskId, 1, "message_args")).isEqualTo(step1Args);
        assertThat(stepField(taskId, 3, "status")).isEqualTo("SUCCEEDED");

        JsonNode succeeded = taskDetail(login, taskId, "zh-CN");
        assertThat(succeeded.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.path("retry").path("retryCount").asInt()).isEqualTo(1);
        assertThat(succeeded.path("retry").path("lastError").isNull()).isTrue();
        assertThat(succeeded.path("failure").isNull()).isTrue();
        assertThat(succeeded.path("steps").get(1).path("attempts").size()).isEqualTo(2);
        assertThat(succeeded.path("result").path("available").asBoolean()).isTrue();
        mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk());
    }

    @Test
    void exhaustsRetryBudgetAndSkipsRemainingSteps() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 90, 60, new Color(40, 110, 60));
        String second = uploadJpeg(login, "travel-postcard", 60, 90, new Color(110, 40, 60));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        injectFailure("GENERATE", 3, true);

        runQueuedTasks();
        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(1);
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(1);

        makeRetryDue(taskId);
        runQueuedTasks();
        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(2);
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(2);

        makeRetryDue(taskId);
        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("FAILED");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(2);
        assertThat(taskField(taskId, "failure_code")).isEqualTo("GENERATION_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_simulated FROM task WHERE id = ?", Boolean.class, taskId)).isTrue();
        assertThat(taskField(taskId, "last_error_code")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM task WHERE id = ?", LocalDateTime.class, taskId)).isNull();
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(3);
        assertThat(attemptCount(taskId, 2)).isEqualTo(3);
        assertThat(attemptStatus(taskId, 2, 1)).isEqualTo("FAILED");
        assertThat(attemptStatus(taskId, 2, 2)).isEqualTo("FAILED");
        assertThat(attemptStatus(taskId, 2, 3)).isEqualTo("FAILED");
        assertThat(stepField(taskId, 3, "status")).isEqualTo("SKIPPED");
        assertThat(attemptCount(taskId, 3)).isZero();

        JsonNode detail = taskDetail(login, taskId, "en-US");
        assertThat(detail.path("status").asText()).isEqualTo("FAILED");
        assertThat(detail.path("failure").path("code").asText()).isEqualTo("GENERATION_FAILED");
        assertThat(detail.path("failure").path("simulated").asBoolean()).isTrue();
        assertThat(detail.path("failure").path("message").asText())
                .startsWith("Simulated failure:");
        assertThat(detail.path("retry").path("lastError").isNull()).isTrue();
        assertThat(detail.path("steps").get(2).path("status").asText()).isEqualTo("SKIPPED");
    }

    @Test
    void keepsRetryBudgetsIndependentAcrossSteps() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 80, 60, new Color(70, 70, 130));
        String second = uploadJpeg(login, "travel-postcard", 60, 80, new Color(130, 70, 70));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        injectFailure("GENERATE", 1, true);

        runQueuedTasks();
        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(1);

        injectFailure("FINALIZE", 1, true);
        makeRetryDue(taskId);
        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(2);
        assertThat(taskField(taskId, "current_stage")).isEqualTo("FINALIZE");
        assertThat(taskField(taskId, "last_error_code")).isEqualTo("RESULT_RENDER_FAILED");
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(2);
        assertThat(stepField(taskId, 2, "status")).isEqualTo("SUCCEEDED");
        assertThat(stepInt(taskId, 3, "attempt_count")).isEqualTo(1);

        makeRetryDue(taskId);
        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("SUCCEEDED");
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(2);
        assertThat(stepInt(taskId, 3, "attempt_count")).isEqualTo(2);
        assertThat(attemptCount(taskId, 3)).isEqualTo(2);
        assertThat(attemptStatus(taskId, 3, 1)).isEqualTo("FAILED");
        assertThat(attemptStatus(taskId, 3, 2)).isEqualTo("SUCCEEDED");
    }

    @Test
    void mapsUnexpectedPipelineFailureToInternalError() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 70, 70, new Color(90, 120, 30));
        String second = uploadJpeg(login, "travel-postcard", 70, 70, new Color(30, 90, 120));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        zeroSimulationDelays();
        jdbcTemplate.update("DELETE FROM simulation_setting WHERE id = 1");

        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskField(taskId, "last_error_code")).isEqualTo("INTERNAL_ERROR");
        assertThat(stepField(taskId, 1, "status")).isEqualTo("FAILED");
        assertThat(stepField(taskId, 1, "error_code")).isEqualTo("INTERNAL_ERROR");
        assertThat(taskField(taskId, "failure_code")).isNull();

        JsonNode detail = taskDetail(login, taskId, "zh-CN");
        assertThat(detail.path("retry").path("lastError").path("code").asText())
                .isEqualTo("INTERNAL_ERROR");
        assertThat(detail.path("retry").path("lastError").path("simulated").asBoolean()).isFalse();
        assertThat(detail.path("failure").isNull()).isTrue();
    }

    private String taskField(String taskId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM task WHERE id = ?",
                String.class, taskId);
    }

    private int taskInt(String taskId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM task WHERE id = ?",
                Integer.class, taskId);
    }

    private String stepField(String taskId, int seq, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM task_step WHERE task_id = ? AND seq = ?",
                String.class, taskId, seq);
    }

    private int stepInt(String taskId, int seq, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM task_step WHERE task_id = ? AND seq = ?",
                Integer.class, taskId, seq);
    }

    private LocalDateTime stepTime(String taskId, int seq, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM task_step WHERE task_id = ? AND seq = ?",
                LocalDateTime.class, taskId, seq);
    }

    private int attemptCount(String taskId, int seq) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM task_step_attempt a
                  JOIN task_step s ON s.id = a.step_id
                 WHERE a.task_id = ? AND s.seq = ?
                """, Integer.class, taskId, seq);
    }

    private String attemptStatus(String taskId, int seq, int attemptNo) {
        return jdbcTemplate.queryForObject("""
                SELECT a.status
                  FROM task_step_attempt a
                  JOIN task_step s ON s.id = a.step_id
                 WHERE a.task_id = ? AND s.seq = ? AND a.attempt_no = ?
                """, String.class, taskId, seq, attemptNo);
    }
}
