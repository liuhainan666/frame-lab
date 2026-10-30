package com.framelab.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "framelab.storage")
public class StorageProperties {

    private Path root = Path.of("./storage");
    private long maxFileSizeBytes = 10 * 1024 * 1024L;
    private long maxRequestFileSizeBytes = 11 * 1024 * 1024L;
    private long maxPixels = 40_000_000L;
    private int maxDimension = 8192;
    private int maxNormalizedDimension = 4096;

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public long getMaxRequestFileSizeBytes() {
        return maxRequestFileSizeBytes;
    }

    public void setMaxRequestFileSizeBytes(long maxRequestFileSizeBytes) {
        this.maxRequestFileSizeBytes = maxRequestFileSizeBytes;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public void setMaxDimension(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    public int getMaxNormalizedDimension() {
        return maxNormalizedDimension;
    }

    public void setMaxNormalizedDimension(int maxNormalizedDimension) {
        this.maxNormalizedDimension = maxNormalizedDimension;
    }
}
