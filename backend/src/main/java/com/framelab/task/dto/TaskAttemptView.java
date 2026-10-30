package com.framelab.task.dto;

import java.time.Instant;

public record TaskAttemptView(int attemptNo,
                              String status,
                              String errorCode,
                              String errorMessage,
                              Instant startedAt,
                              Instant finishedAt) {
}
