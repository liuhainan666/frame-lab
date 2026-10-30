package com.framelab.task.dto;

public record TaskResultView(boolean available, String url, Integer width, Integer height, Long sizeBytes) {
}
