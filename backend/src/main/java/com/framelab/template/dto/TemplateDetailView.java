package com.framelab.template.dto;

import java.util.List;

public record TemplateDetailView(
        String id,
        String title,
        TemplateCategoryView category,
        String coverUrl,
        String description,
        int requiredImageCount,
        CanvasView canvas,
        List<String> acceptedMimeTypes,
        long maxFileSizeBytes) {
}
