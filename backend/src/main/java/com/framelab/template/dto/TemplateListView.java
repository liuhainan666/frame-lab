package com.framelab.template.dto;

import java.util.List;

public record TemplateListView(List<TemplateSummaryView> items, int total) {
}
