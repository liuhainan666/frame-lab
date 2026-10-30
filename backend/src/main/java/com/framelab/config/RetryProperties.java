package com.framelab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "framelab.retry")
public class RetryProperties {

    private int maxStepAttempts = 3;
    private int backoffBaseSeconds = 2;
    private int backoffMaxSeconds = 30;

    public int getMaxStepAttempts() {
        return maxStepAttempts;
    }

    public void setMaxStepAttempts(int maxStepAttempts) {
        this.maxStepAttempts = maxStepAttempts;
    }

    public int getBackoffBaseSeconds() {
        return backoffBaseSeconds;
    }

    public void setBackoffBaseSeconds(int backoffBaseSeconds) {
        this.backoffBaseSeconds = backoffBaseSeconds;
    }

    public int getBackoffMaxSeconds() {
        return backoffMaxSeconds;
    }

    public void setBackoffMaxSeconds(int backoffMaxSeconds) {
        this.backoffMaxSeconds = backoffMaxSeconds;
    }
}
