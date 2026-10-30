package com.framelab.task.worker;

import java.util.concurrent.atomic.AtomicBoolean;

import com.framelab.infrastructure.storage.ObjectStorage;
import com.framelab.infrastructure.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class StartupRecovery implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final StartupRecoveryService recoveryService;
    private final ObjectStorage objectStorage;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StartupRecovery(StartupRecoveryService recoveryService, ObjectStorage objectStorage) {
        this.recoveryService = recoveryService;
        this.objectStorage = objectStorage;
    }

    @Override
    public void start() {
        StartupRecoveryService.RecoveryReport report = recoveryService.recover();
        for (String taskId : report.taskIds()) {
            try {
                objectStorage.deleteTaskTemp(taskId);
            } catch (StorageException ex) {
                log.warn("recovery.temp_cleanup_failed taskId={}", taskId, ex);
            }
        }
        running.set(true);
        log.info("recovery.completed recovered={} succeeded={}", report.recovered(), report.succeeded());
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
