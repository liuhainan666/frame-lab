package com.framelab.task.dto;

public record TaskResultView(
        boolean available,
        String kind,
        String mimeType,
        String url,
        String posterUrl,
        Integer width,
        Integer height,
        Integer durationMs,
        Long sizeBytes) {
}
