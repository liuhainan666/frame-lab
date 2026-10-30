package com.framelab.task.dto;

import java.time.Instant;

public record TaskRetryView(int retryCount,
                            int attemptCount,
                            int maxAttempts,
                            Instant nextRetryAt,
                            TaskErrorView lastError) {
}
