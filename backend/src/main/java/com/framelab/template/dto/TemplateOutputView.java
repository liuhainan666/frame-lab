package com.framelab.template.dto;

public record TemplateOutputView(String kind, Integer durationMs, int width, int height, boolean hasAudio) {
}
