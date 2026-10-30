package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.framelab.task.TaskClaimService;
import com.framelab.task.TaskSubmissionOutcome;
import com.framelab.task.domain.TaskStage;
import com.framelab.task.dto.TaskSubmitRequest;
import com.framelab.simulation.FailureInjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TaskConcurrencyTest extends TaskTestSupport {

    private final List<String> taskIds = new ArrayList<>();
    private final List<String> assetIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedData() throws Exception {
        if (!taskIds.isEmpty()) {
            String placeholders = placeholders(taskIds.size());
            List<String> resultKeys = jdbcTemplate.queryForList(
                    "SELECT result_storage_key FROM task WHERE id IN (" + placeholders + ")",
                    String.class, taskIds.toArray());
            for (String key : resultKeys) {
                if (key != null) {
                    Files.deleteIfExists(STORAGE_ROOT.resolve(key));
                }
            }
            jdbcTemplate.update("DELETE FROM task WHERE id IN (" + placeholders + ")",
                    taskIds.toArray());
        }
        if (!assetIds.isEmpty()) {
            String placeholders = placeholders(assetIds.size());
            List<String> storageKeys = jdbcTemplate.queryForList(
                    "SELECT storage_key FROM upload_asset WHERE id IN (" + placeholders + ")",
                    String.class, assetIds.toArray());
            for (String key : storageKeys) {
                Files.deleteIfExists(STORAGE_ROOT.resolve(key));
            }
            jdbcTemplate.update("DELETE FROM upload_asset WHERE id IN (" + placeholders + ")",
                    assetIds.toArray());
        }
        jdbcTemplate.update("""
                UPDATE simulation_setting
                   SET material_check_delay_ms = 800,
                       generate_delay_ms = 3000,
                       finalize_delay_ms = 1200,
                       fail_stage = NULL,
                       fail_times = 0,
                       fail_retryable = 0
                 WHERE id = 1
                """);
    }

    @Test
    void runsWithoutTestTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void concurrentSubmissionCreatesExactlyOneTask() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = track(uploadJpeg(login, "travel-postcard", 70, 70, new Color(40, 80, 120)));
        String second = track(uploadJpeg(login, "travel-postcard", 70, 70, new Color(120, 80, 40)));
        String requestId = newRequestId();
        TaskSubmitRequest request = new TaskSubmitRequest("travel-postcard", List.of(first, second), requestId);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TaskSubmissionOutcome>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return taskSubmissionService.submit(1L, request);
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            int created = 0;
            for (Future<TaskSubmissionOutcome> future : futures) {
                TaskSubmissionOutcome outcome = future.get(20, TimeUnit.SECONDS);
                ids.add(outcome.task().id());
                if (!outcome.replay()) {
                    created++;
                }
            }
            assertThat(ids).hasSize(1);
            assertThat(created).isEqualTo(1);
            String taskId = ids.iterator().next();
            taskIds.add(taskId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM task WHERE user_id = 1 AND client_request_id = ?",
                    Integer.class, requestId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM task_material WHERE task_id = ?", Integer.class, taskId)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM task_step WHERE task_id = ?", Integer.class, taskId)).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void injectedFailureIsConsumedExactlyOnceAcrossThreads() throws Exception {
        jdbcTemplate.update("""
                UPDATE simulation_setting
                   SET material_check_delay_ms = 0, generate_delay_ms = 0, finalize_delay_ms = 0,
                       fail_stage = 'GENERATE', fail_times = 1, fail_retryable = 1
                 WHERE id = 1
                """);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FailureInjection>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return simulationService.consumeFailure(TaskStage.GENERATE);
                }));
            }
            start.countDown();
            List<FailureInjection> injections = new ArrayList<>();
            for (Future<FailureInjection> future : futures) {
                FailureInjection injection = future.get(20, TimeUnit.SECONDS);
                if (injection != null) {
                    injections.add(injection);
                }
            }
            assertThat(injections).hasSize(1);
            assertThat(injections.get(0).retryable()).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT fail_times FROM simulation_setting WHERE id = 1", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT fail_stage FROM simulation_setting WHERE id = 1", String.class)).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoWorkersProcessFourTasksWithoutCrossTalk() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = track(uploadJpeg(login, "travel-postcard", 90, 60, new Color(15, 80, 60)));
        String second = track(uploadJpeg(login, "travel-postcard", 60, 90, new Color(80, 15, 60)));
        for (int index = 0; index < 4; index++) {
            String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                    .path("id").asText();
            taskIds.add(taskId);
        }
        zeroSimulationDelays();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                workers.add(pool.submit(() -> {
                    TaskClaimService.ClaimedStep claim;
                    while ((claim = taskClaimService.claimNext()) != null) {
                        taskPipeline.run(claim);
                    }
                    return null;
                }));
            }
            for (Future<?> worker : workers) {
                worker.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        String placeholders = placeholders(taskIds.size());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task WHERE id IN (" + placeholders + ") AND status = 'SUCCEEDED'",
                Integer.class, taskIds.toArray())).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step WHERE task_id IN (" + placeholders + ") AND status = 'SUCCEEDED'",
                Integer.class, taskIds.toArray())).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id IN (" + placeholders
                        + ") AND status = 'SUCCEEDED'", Integer.class, taskIds.toArray())).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step WHERE task_id IN (" + placeholders + ") AND status = 'RUNNING'",
                Integer.class, taskIds.toArray())).isZero();

        List<String> resultKeys = jdbcTemplate.queryForList(
                "SELECT result_storage_key FROM task WHERE id IN (" + placeholders + ")",
                String.class, taskIds.toArray());
        assertThat(resultKeys).hasSize(4);
        assertThat(new HashSet<>(resultKeys)).hasSize(4);
        for (String key : resultKeys) {
            Path path = STORAGE_ROOT.resolve(key);
            assertThat(path).exists();
            assertThat(Files.size(path)).isPositive();
            assertThat(key).matches("results/1/[0-9a-f-]{36}\\.jpg");
        }
        for (String taskId : taskIds) {
            assertThat(resultKeys).contains("results/1/" + taskId + ".jpg");
        }
    }

    private String track(String assetId) {
        assetIds.add(assetId);
        return assetId;
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
