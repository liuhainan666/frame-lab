package com.framelab.template.dto;

import java.util.List;

import com.framelab.template.TemplateKind;

public record TemplateSummaryView(
        String id,
        String title,
        TemplateKind kind,
        TemplateCategoryView category,
        String coverUrl,
        String description,
        int requiredImageCount,
        CanvasView canvas,
        List<String> effectTags,
        TemplateOutputView output) {
}
