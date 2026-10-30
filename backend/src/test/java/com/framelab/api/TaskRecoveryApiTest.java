package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.framelab.task.TaskClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TaskRecoveryApiTest extends TaskTestSupport {

    @Test
    void recoversRunningTaskAndResumesFromInterruptedStep() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 100, 70, new Color(30, 60, 140));
        String second = uploadJpeg(login, "travel-postcard", 70, 100, new Color(140, 60, 30));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        zeroSimulationDelays();

        TaskClaimService.ClaimedStep step1 = taskClaimService.claimNext();
        assertThat(step1).isNotNull();
        taskStepService.complete(step1, "{\"checked\":2,\"required\":2}");

        assertThat(taskField(taskId, "status")).isEqualTo("RUNNING");
        assertThat(stepField(taskId, 2, "status")).isEqualTo("RUNNING");
        assertThat(attemptStatus(taskId, 2, 1)).isEqualTo("RUNNING");
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(1);
        LocalDateTime step1Started = stepTime(taskId, 1, "started_at");
        LocalDateTime step1Finished = stepTime(taskId, 1, "finished_at");
        String step1Args = stepField(taskId, 1, "message_args");
        LocalDateTime taskStarted = jdbcTemplate.queryForObject(
                "SELECT started_at FROM task WHERE id = ?", LocalDateTime.class, taskId);

        var report = startupRecoveryService.recover();

        assertThat(report.recovered()).isEqualTo(1);
        assertThat(taskField(taskId, "status")).isEqualTo("QUEUED");
        assertThat(taskInt(taskId, "resume_count")).isEqualTo(1);
        assertThat(taskInt(taskId, "retry_count")).isZero();
        assertThat(taskField(taskId, "current_stage")).isEqualTo("GENERATE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT started_at FROM task WHERE id = ?", LocalDateTime.class, taskId))
                .isEqualTo(taskStarted);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT finished_at FROM task WHERE id = ?", LocalDateTime.class, taskId)).isNull();
        assertThat(taskField(taskId, "last_error_code")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM task WHERE id = ?", LocalDateTime.class, taskId)).isNull();

        assertThat(stepField(taskId, 1, "status")).isEqualTo("SUCCEEDED");
        assertThat(stepTime(taskId, 1, "started_at")).isEqualTo(step1Started);
        assertThat(stepTime(taskId, 1, "finished_at")).isEqualTo(step1Finished);
        assertThat(stepField(taskId, 1, "message_args")).isEqualTo(step1Args);
        assertThat(stepField(taskId, 2, "status")).isEqualTo("PENDING");
        assertThat(stepInt(taskId, 2, "attempt_count")).isZero();
        assertThat(stepTime(taskId, 2, "started_at")).isNull();
        assertThat(stepTime(taskId, 2, "finished_at")).isNull();
        assertThat(stepField(taskId, 2, "error_code")).isNull();
        assertThat(attemptStatus(taskId, 2, 1)).isEqualTo("INTERRUPTED");
        assertThat(attemptFinished(taskId, 2, 1)).isNotNull();
        assertThat(stepField(taskId, 3, "status")).isEqualTo("PENDING");

        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("SUCCEEDED");
        assertThat(taskInt(taskId, "resume_count")).isEqualTo(1);
        assertThat(taskInt(taskId, "retry_count")).isZero();
        assertThat(stepInt(taskId, 2, "attempt_count")).isEqualTo(1);
        assertThat(attemptRowCount(taskId, 2)).isEqualTo(2);
        assertThat(attemptStatus(taskId, 2, 1)).isEqualTo("INTERRUPTED");
        assertThat(attemptStatus(taskId, 2, 2)).isEqualTo("SUCCEEDED");
        assertThat(stepField(taskId, 3, "status")).isEqualTo("SUCCEEDED");
        assertThat(stepTime(taskId, 1, "started_at")).isEqualTo(step1Started);
        assertThat(stepTime(taskId, 1, "finished_at")).isEqualTo(step1Finished);
        assertThat(stepField(taskId, 1, "message_args")).isEqualTo(step1Args);

        mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk());
        JsonNode detail = taskDetail(login, taskId, "zh-CN");
        assertThat(detail.path("resumeCount").asInt()).isEqualTo(1);
        assertThat(detail.path("retry").path("attemptCount").asInt()).isEqualTo(1);
        assertThat(detail.path("steps").get(1).path("attempts").size()).isEqualTo(2);
        assertThat(detail.path("steps").get(1).path("attempts").get(0).path("status").asText())
                .isEqualTo("INTERRUPTED");
        assertThat(detail.path("steps").get(1).path("attempts").get(0).path("errorMessage").asText())
                .isEqualTo("服务重启中断");
    }

    @Test
    void multipleRecoveriesKeepResumeCountAndSingleResult() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 90, 60, new Color(40, 130, 90));
        String second = uploadJpeg(login, "travel-postcard", 60, 90, new Color(130, 40, 90));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        zeroSimulationDelays();

        TaskClaimService.ClaimedStep step1 = taskClaimService.claimNext();
        taskStepService.complete(step1, "{\"checked\":2,\"required\":2}");
        startupRecoveryService.recover();
        assertThat(taskInt(taskId, "resume_count")).isEqualTo(1);

        TaskClaimService.ClaimedStep step2 = taskClaimService.claimNext();
        assertThat(step2).isNotNull();
        taskStepService.complete(step2, "{\"width\":1600,\"height\":1200}");
        assertThat(stepField(taskId, 3, "status")).isEqualTo("RUNNING");

        var secondReport = startupRecoveryService.recover();
        assertThat(secondReport.recovered()).isEqualTo(1);
        assertThat(taskField(taskId, "status")).isEqualTo("QUEUED");
        assertThat(taskInt(taskId, "resume_count")).isEqualTo(2);
        assertThat(stepField(taskId, 3, "status")).isEqualTo("PENDING");
        assertThat(stepInt(taskId, 3, "attempt_count")).isZero();
        assertThat(attemptStatus(taskId, 3, 1)).isEqualTo("INTERRUPTED");

        runQueuedTasks();

        assertThat(taskField(taskId, "status")).isEqualTo("SUCCEEDED");
        assertThat(taskInt(taskId, "resume_count")).isEqualTo(2);
        assertThat(taskInt(taskId, "retry_count")).isZero();
        assertThat(stepInt(taskId, 3, "attempt_count")).isEqualTo(1);
        assertThat(attemptRowCount(taskId, 3)).isEqualTo(2);
        assertThat(attemptStatus(taskId, 3, 2)).isEqualTo("SUCCEEDED");

        String resultKey = jdbcTemplate.queryForObject(
                "SELECT result_storage_key FROM task WHERE id = ?", String.class, taskId);
        assertThat(resultKey).isEqualTo("results/1/" + taskId + ".jpg");
        Path resultDirectory = STORAGE_ROOT.resolve("results/1");
        try (var files = Files.list(resultDirectory)) {
            assertThat(files.filter(path -> path.getFileName().toString().startsWith(taskId)).toList())
                    .hasSize(1);
        }
    }

    @Test
    void recoveryLeavesRetryingTaskUntouched() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 80, 60, new Color(120, 110, 40));
        String second = uploadJpeg(login, "travel-postcard", 60, 80, new Color(40, 120, 110));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
        injectFailure("GENERATE", 1, true);

        runQueuedTasks();
        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        LocalDateTime nextRetry = jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM task WHERE id = ?", LocalDateTime.class, taskId);
        assertThat(nextRetry).isNotNull();

        var report = startupRecoveryService.recover();

        assertThat(report.recovered()).isZero();
        assertThat(taskField(taskId, "status")).isEqualTo("RETRYING");
        assertThat(taskInt(taskId, "resume_count")).isZero();
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM task WHERE id = ?", LocalDateTime.class, taskId))
                .isEqualTo(nextRetry);
        assertThat(taskField(taskId, "last_error_code")).isEqualTo("GENERATION_FAILED");
        assertThat(stepField(taskId, 2, "status")).isEqualTo("FAILED");

        makeRetryDue(taskId);
        runQueuedTasks();
        assertThat(taskField(taskId, "status")).isEqualTo("SUCCEEDED");
        assertThat(taskInt(taskId, "resume_count")).isZero();
        assertThat(taskInt(taskId, "retry_count")).isEqualTo(1);
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

    private int attemptRowCount(String taskId, int seq) {
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

    private LocalDateTime attemptFinished(String taskId, int seq, int attemptNo) {
        return jdbcTemplate.queryForObject("""
                SELECT a.finished_at
                  FROM task_step_attempt a
                  JOIN task_step s ON s.id = a.step_id
                 WHERE a.task_id = ? AND s.seq = ? AND a.attempt_no = ?
                """, LocalDateTime.class, taskId, seq, attemptNo);
    }
}
