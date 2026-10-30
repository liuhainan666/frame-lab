package com.framelab.template.dto;

public record TemplateSummaryView(
        String id,
        String title,
        TemplateCategoryView category,
        String coverUrl,
        String description,
        int requiredImageCount,
        CanvasView canvas) {
}
