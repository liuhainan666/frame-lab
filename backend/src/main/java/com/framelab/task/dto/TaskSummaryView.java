package com.framelab.task.dto;

import java.time.Instant;

public record TaskSummaryView(String id,
                              String templateId,
                              String templateTitle,
                              String templateCoverUrl,
                              String status,
                              String currentStage,
                              TaskRetryView retry,
                              int resumeCount,
                              TaskFailureView failure,
                              TaskResultView result,
                              Instant createdAt,
                              Instant updatedAt,
                              Instant finishedAt) {
}
