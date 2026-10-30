package com.framelab.simulation;

import java.time.LocalDateTime;

import com.framelab.task.domain.TaskStage;

public class SimulationRow {

    private int materialCheckDelayMs;
    private int generateDelayMs;
    private int finalizeDelayMs;
    private TaskStage failStage;
    private int failTimes;
    private boolean failRetryable;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public long delayMs(TaskStage stage) {
        return switch (stage) {
            case MATERIAL_CHECK -> materialCheckDelayMs;
            case GENERATE -> generateDelayMs;
            case FINALIZE -> finalizeDelayMs;
        };
    }

    public int getMaterialCheckDelayMs() {
        return materialCheckDelayMs;
    }

    public void setMaterialCheckDelayMs(int materialCheckDelayMs) {
        this.materialCheckDelayMs = materialCheckDelayMs;
    }

    public int getGenerateDelayMs() {
        return generateDelayMs;
    }

    public void setGenerateDelayMs(int generateDelayMs) {
        this.generateDelayMs = generateDelayMs;
    }

    public int getFinalizeDelayMs() {
        return finalizeDelayMs;
    }

    public void setFinalizeDelayMs(int finalizeDelayMs) {
        this.finalizeDelayMs = finalizeDelayMs;
    }

    public TaskStage getFailStage() {
        return failStage;
    }

    public void setFailStage(TaskStage failStage) {
        this.failStage = failStage;
    }

    public int getFailTimes() {
        return failTimes;
    }

    public void setFailTimes(int failTimes) {
        this.failTimes = failTimes;
    }

    public boolean isFailRetryable() {
        return failRetryable;
    }

    public void setFailRetryable(boolean failRetryable) {
        this.failRetryable = failRetryable;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
