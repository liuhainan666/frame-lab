package com.framelab.task.dto;

import java.time.Instant;
import java.util.List;

public record TaskStepView(int seq,
                           String stage,
                           String status,
                           int attemptCount,
                           String message,
                           String errorCode,
                           Instant startedAt,
                           Instant finishedAt,
                           List<TaskAttemptView> attempts) {
}
