package com.framelab.task;

import com.framelab.config.RetryProperties;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private final long baseSeconds;
    private final long maxSeconds;

    public RetryPolicy(RetryProperties properties) {
        this.baseSeconds = Math.max(1, Math.min(60, properties.getBackoffBaseSeconds()));
        this.maxSeconds = Math.max(baseSeconds, Math.min(600, properties.getBackoffMaxSeconds()));
    }

    public int delaySeconds(int attemptCount) {
        long delay = baseSeconds;
        int remaining = Math.max(1, attemptCount) - 1;
        while (remaining > 0 && delay < maxSeconds) {
            delay = Math.min(maxSeconds, delay * 2);
            remaining--;
        }
        return (int) Math.min(delay, Integer.MAX_VALUE);
    }
}
