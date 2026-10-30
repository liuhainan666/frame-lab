package com.framelab.template.dto;

import java.util.List;

import com.framelab.template.TemplateKind;

public record TemplateDetailView(
        String id,
        String title,
        TemplateKind kind,
        TemplateCategoryView category,
        String coverUrl,
        String description,
        int requiredImageCount,
        CanvasView canvas,
        List<String> acceptedMimeTypes,
        long maxFileSizeBytes,
        List<String> effectTags,
        TemplateOutputView output) {
}
