package com.framelab.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "framelab.ffmpeg")
public class FfmpegProperties {

    private boolean enabled = true;
    private String binary = "ffmpeg";
    private String probe = "ffprobe";
    private Duration timeout = Duration.ofSeconds(180);
    private int maxConcurrent = 1;
    private long maxOutputBytes = 200L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBinary() {
        return binary;
    }

    public void setBinary(String binary) {
        this.binary = binary;
    }

    public String getProbe() {
        return probe;
    }

    public void setProbe(String probe) {
        this.probe = probe;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }
}
