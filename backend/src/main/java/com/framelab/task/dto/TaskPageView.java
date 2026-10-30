package com.framelab.task.dto;

import java.util.List;

public record TaskPageView(List<TaskSummaryView> items,
                           int page,
                           int size,
                           long total,
                           int totalPages,
                           boolean hasNext) {
}
