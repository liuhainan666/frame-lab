package com.framelab.task.domain;

public enum FailureCode {

    MATERIAL_CHECK_FAILED(TaskStage.MATERIAL_CHECK, false),
    GENERATION_FAILED(TaskStage.GENERATE, false),
    RESULT_RENDER_FAILED(TaskStage.FINALIZE, true),
    STORAGE_UNAVAILABLE(null, true),
    INTERNAL_ERROR(null, true);

    private final TaskStage stage;
    private final boolean retryable;

    FailureCode(TaskStage stage, boolean retryable) {
        this.stage = stage;
        this.retryable = retryable;
    }

    public TaskStage stage() {
        return stage;
    }

    public boolean retryable() {
        return retryable;
    }

    public static FailureCode forStage(TaskStage stage) {
        return switch (stage) {
            case MATERIAL_CHECK -> MATERIAL_CHECK_FAILED;
            case GENERATE -> GENERATION_FAILED;
            case FINALIZE -> RESULT_RENDER_FAILED;
        };
    }
}
