package com.framelab.task.domain;

public class TaskFailureException extends RuntimeException {

    private final TaskStage stage;
    private final FailureCode code;
    private final boolean simulated;
    private final boolean retryable;
    private final String detail;

    public TaskFailureException(TaskStage stage, FailureCode code, boolean simulated, boolean retryable,
                                String detail) {
        super(code.name(), null, false, false);
        this.stage = stage;
        this.code = code;
        this.simulated = simulated;
        this.retryable = retryable;
        this.detail = detail;
    }

    public static TaskFailureException simulated(TaskStage stage, boolean retryable) {
        return new TaskFailureException(stage, FailureCode.forStage(stage), true, retryable,
                "simulated failure at " + stage.name());
    }

    public TaskStage stage() {
        return stage;
    }

    public FailureCode code() {
        return code;
    }

    public boolean simulated() {
        return simulated;
    }

    public boolean retryable() {
        return retryable;
    }

    public String detail() {
        return detail;
    }
}
