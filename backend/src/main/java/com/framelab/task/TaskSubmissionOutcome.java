package com.framelab.task;

import com.framelab.task.dto.TaskDetailView;

public record TaskSubmissionOutcome(TaskDetailView task, boolean replay) {
}
