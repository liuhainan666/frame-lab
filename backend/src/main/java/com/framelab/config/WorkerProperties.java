package com.framelab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "framelab.worker")
public class WorkerProperties {

    private boolean enabled = true;
    private int threads = 2;
    private long pollMs = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public long getPollMs() {
        return pollMs;
    }

    public void setPollMs(long pollMs) {
        this.pollMs = pollMs;
    }
}
