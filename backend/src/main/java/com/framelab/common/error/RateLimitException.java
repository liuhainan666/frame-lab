package com.framelab.common.error;

public class RateLimitException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, new Object[0], null);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
