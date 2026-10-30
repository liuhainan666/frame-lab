package com.framelab.task.domain;

public enum TaskStage {
    MATERIAL_CHECK(1),
    GENERATE(2),
    FINALIZE(3);

    private final int seq;

    TaskStage(int seq) {
        this.seq = seq;
    }

    public int seq() {
        return seq;
    }

    public static TaskStage bySeq(int seq) {
        for (TaskStage stage : values()) {
            if (stage.seq == seq) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown task stage seq: " + seq);
    }
}
