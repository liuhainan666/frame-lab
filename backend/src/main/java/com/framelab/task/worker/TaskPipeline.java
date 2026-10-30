package com.framelab.task.worker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import com.framelab.infrastructure.storage.ObjectStorage;
import com.framelab.infrastructure.storage.StorageException;
import com.framelab.infrastructure.storage.StoredObject;
import com.framelab.simulation.FailureInjection;
import com.framelab.simulation.SimulationRow;
import com.framelab.simulation.SimulationService;
import com.framelab.task.TaskClaimService;
import com.framelab.task.TaskMapper;
import com.framelab.task.TaskMaterialRow;
import com.framelab.task.TaskRow;
import com.framelab.task.TaskStepService;
import com.framelab.task.domain.FailureCode;
import com.framelab.task.domain.TaskFailureException;
import com.framelab.task.domain.TaskStage;
import com.framelab.task.render.LayoutPlanner;
import com.framelab.task.render.LayoutSlot;
import com.framelab.task.render.MaterialImageReader;
import com.framelab.task.render.ResultRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);

    private final TaskMapper taskMapper;
    private final TaskStepService taskStepService;
    private final SimulationService simulationService;
    private final MaterialImageReader materialImageReader;
    private final LayoutPlanner layoutPlanner;
    private final ResultRenderer resultRenderer;
    private final ObjectStorage objectStorage;

    public TaskPipeline(TaskMapper taskMapper,
                        TaskStepService taskStepService,
                        SimulationService simulationService,
                        MaterialImageReader materialImageReader,
                        LayoutPlanner layoutPlanner,
                        ResultRenderer resultRenderer,
                        ObjectStorage objectStorage) {
        this.taskMapper = taskMapper;
        this.taskStepService = taskStepService;
        this.simulationService = simulationService;
        this.materialImageReader = materialImageReader;
        this.layoutPlanner = layoutPlanner;
        this.resultRenderer = resultRenderer;
        this.objectStorage = objectStorage;
    }

    public void run(TaskClaimService.ClaimedStep claim) {
        TaskClaimService.ClaimedStep current = claim;
        try {
            while (current != null) {
                SimulationRow simulation = simulationService.current();
                sleep(simulation.delayMs(current.stage()));
                FailureInjection injection = simulationService.consumeFailure(current.stage());
                if (injection != null) {
                    log.info("task.failure_injected taskId={} stage={} retryable={}",
                            current.taskId(), current.stage(), injection.retryable());
                    failSafely(current, TaskFailureException.simulated(current.stage(), injection.retryable()));
                    return;
                }

                TaskRow task = taskMapper.findById(current.taskId());
                if (task == null) {
                    throw new IllegalStateException("Task disappeared while running");
                }
                List<TaskMaterialRow> materials = taskMapper.findMaterials(current.taskId());

                switch (current.stage()) {
                    case MATERIAL_CHECK -> {
                        checkMaterials(task.getUserId(), materials);
                        int checked = materials.size();
                        String args = "{\"checked\":%d,\"required\":%d}".formatted(checked, checked);
                        current = taskStepService.complete(current, args);
                    }
                    case GENERATE -> {
                        List<LayoutSlot> slots = plan(materials.size(), task);
                        String args = "{\"width\":%d,\"height\":%d}"
                                .formatted(task.getCanvasWidth(), task.getCanvasHeight());
                        log.debug("task.layout_ready taskId={} slots={}", current.taskId(), slots.size());
                        current = taskStepService.complete(current, args);
                    }
                    case FINALIZE -> {
                        finish(current, task, materials);
                        return;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("task.pipeline_interrupted taskId={}", current.taskId());
        } catch (TaskFailureException ex) {
            log.info("task.failed taskId={} stage={} code={} simulated={}",
                    current.taskId(), ex.stage(), ex.code(), ex.simulated());
            failSafely(current, ex);
        } catch (StorageException ex) {
            log.error("task.storage_failed taskId={} stage={}", current.taskId(), current.stage(), ex);
            failSafely(current, new TaskFailureException(current.stage(), FailureCode.STORAGE_UNAVAILABLE,
                    false, true, "storage operation failed"));
        } catch (Exception ex) {
            log.error("task.pipeline_failed taskId={} stage={}", current.taskId(), current.stage(), ex);
            failSafely(current, new TaskFailureException(current.stage(), FailureCode.INTERNAL_ERROR,
                    false, true, "unexpected " + ex.getClass().getSimpleName()));
        }
    }

    private void checkMaterials(long userId, List<TaskMaterialRow> materials) {
        if (materials.isEmpty()) {
            throw new TaskFailureException(TaskStage.MATERIAL_CHECK, FailureCode.MATERIAL_CHECK_FAILED,
                    false, false, "no materials attached");
        }
        for (TaskMaterialRow material : materials) {
            try {
                materialImageReader.read(userId, material.getAssetId());
            } catch (Exception ex) {
                throw new TaskFailureException(TaskStage.MATERIAL_CHECK, FailureCode.MATERIAL_CHECK_FAILED,
                        false, false, "material unreadable: " + material.getAssetId());
            }
        }
    }

    private void finish(TaskClaimService.ClaimedStep current, TaskRow task, List<TaskMaterialRow> materials) {
        List<BufferedImage> images = new ArrayList<>(materials.size());
        for (TaskMaterialRow material : materials) {
            try {
                images.add(materialImageReader.read(task.getUserId(), material.getAssetId()));
            } catch (Exception ex) {
                throw new TaskFailureException(TaskStage.FINALIZE, FailureCode.RESULT_RENDER_FAILED,
                        false, true, "material unreadable at finalize: " + material.getAssetId());
            }
        }
        List<LayoutSlot> slots = plan(images.size(), task);
        byte[] jpeg;
        try {
            jpeg = resultRenderer.render(images, task.getCanvasWidth(), task.getCanvasHeight(),
                    slots, task.getId());
        } catch (RuntimeException ex) {
            log.error("task.render_failed taskId={}", task.getId(), ex);
            throw new TaskFailureException(TaskStage.FINALIZE, FailureCode.RESULT_RENDER_FAILED,
                    false, true, "result rendering failed");
        }
        StoredObject stored = objectStorage.writeResult(task.getUserId(), task.getId(), jpeg);
        try {
            taskStepService.completeFinal(current, "{\"width\":%d,\"height\":%d}"
                            .formatted(task.getCanvasWidth(), task.getCanvasHeight()),
                    stored.storageKey(), task.getCanvasWidth(), task.getCanvasHeight(), jpeg.length);
        } catch (RuntimeException ex) {
            deleteResultQuietly(stored.storageKey());
            throw ex;
        }
        deleteTaskTempQuietly(task.getId());
        log.info("task.succeeded taskId={} bytes={}", task.getId(), jpeg.length);
    }

    private List<LayoutSlot> plan(int count, TaskRow task) {
        boolean generating = task.getCurrentStage() != TaskStage.FINALIZE;
        TaskStage stage = generating ? TaskStage.GENERATE : TaskStage.FINALIZE;
        FailureCode code = generating ? FailureCode.GENERATION_FAILED : FailureCode.RESULT_RENDER_FAILED;
        try {
            return layoutPlanner.plan(count, task.getCanvasWidth(), task.getCanvasHeight());
        } catch (IllegalArgumentException ex) {
            throw new TaskFailureException(stage, code, false, !generating,
                    "layout planning failed");
        }
    }

    private void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    private void failSafely(TaskClaimService.ClaimedStep current, TaskFailureException failure) {
        try {
            taskStepService.fail(current, failure);
        } catch (Exception ex) {
            log.error("task.failure_persist_failed taskId={} stage={}", current.taskId(), current.stage(), ex);
        }
    }

    private void deleteResultQuietly(String storageKey) {
        try {
            objectStorage.delete(storageKey);
        } catch (StorageException ex) {
            log.warn("task.result_cleanup_failed storageKey={}", storageKey, ex);
        }
    }

    private void deleteTaskTempQuietly(String taskId) {
        try {
            objectStorage.deleteTaskTemp(taskId);
        } catch (StorageException ex) {
            log.warn("task.temp_cleanup_failed taskId={}", taskId, ex);
        }
    }
}
