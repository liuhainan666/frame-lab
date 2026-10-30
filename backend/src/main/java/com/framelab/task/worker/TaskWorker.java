package com.framelab.task.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.framelab.config.WorkerProperties;
import com.framelab.task.TaskClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "framelab.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final TaskClaimService taskClaimService;
    private final TaskPipeline taskPipeline;
    private final WorkerProperties workerProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile ExecutorService executor;

    public TaskWorker(TaskClaimService taskClaimService,
                      TaskPipeline taskPipeline,
                      WorkerProperties workerProperties) {
        this.taskClaimService = taskClaimService;
        this.taskPipeline = taskPipeline;
        this.workerProperties = workerProperties;
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        int threads = Math.max(1, workerProperties.getThreads());
        running.set(true);
        executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "framelab-worker-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        for (int index = 0; index < threads; index++) {
            executor.submit(this::loop);
        }
        log.info("worker.started threads={} pollMs={}", threads, workerProperties.getPollMs());
    }

    @Override
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        ExecutorService current = executor;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    current.shutdownNow();
                }
            } catch (InterruptedException ex) {
                current.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("worker.stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void loop() {
        while (running.get()) {
            try {
                TaskClaimService.ClaimedStep claim = taskClaimService.claimNext();
                if (claim == null) {
                    sleep();
                    continue;
                }
                taskPipeline.run(claim);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("worker.loop_failed", ex);
                try {
                    sleep();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void sleep() throws InterruptedException {
        long pollMs = Math.max(50, workerProperties.getPollMs());
        Thread.sleep(pollMs);
    }
}
