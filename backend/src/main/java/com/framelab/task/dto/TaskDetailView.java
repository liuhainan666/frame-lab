package com.framelab.task.dto;

import java.time.Instant;
import java.util.List;

import com.framelab.template.dto.CanvasView;

public record TaskDetailView(String id,
                             String templateId,
                             String templateTitle,
                             String templateCoverUrl,
                             CanvasView canvas,
                             String status,
                             String currentStage,
                             TaskRetryView retry,
                             int resumeCount,
                             List<TaskStepView> steps,
                             List<TaskMaterialView> materials,
                             TaskFailureView failure,
                             TaskResultView result,
                             Instant createdAt,
                             Instant updatedAt,
                             Instant finishedAt) {
}
