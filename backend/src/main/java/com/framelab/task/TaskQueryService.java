package com.framelab.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.error.FieldErrorView;
import com.framelab.common.i18n.LocaleSupport;
import com.framelab.common.i18n.MessageResolver;
import com.framelab.common.time.TimeFormat;
import com.framelab.task.domain.AttemptStatus;
import com.framelab.task.domain.StepStatus;
import com.framelab.task.domain.TaskStatus;
import com.framelab.task.dto.TaskAttemptView;
import com.framelab.task.dto.TaskDetailView;
import com.framelab.task.dto.TaskErrorView;
import com.framelab.task.dto.TaskFailureView;
import com.framelab.task.dto.TaskMaterialView;
import com.framelab.task.dto.TaskPageView;
import com.framelab.task.dto.TaskResultView;
import com.framelab.task.dto.TaskRetryView;
import com.framelab.task.dto.TaskStepView;
import com.framelab.task.dto.TaskSummaryView;
import com.framelab.template.TemplateKind;
import com.framelab.template.dto.CanvasView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {

    private static final Logger log = LoggerFactory.getLogger(TaskQueryService.class);
    private static final TypeReference<Map<String, Object>> ARG_MAP = new TypeReference<>() {
    };

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskStepAttemptMapper taskStepAttemptMapper;
    private final MessageResolver messages;
    private final ObjectMapper objectMapper;

    public TaskQueryService(TaskMapper taskMapper,
                            TaskStepMapper taskStepMapper,
                            TaskStepAttemptMapper taskStepAttemptMapper,
                            MessageResolver messages,
                            ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskStepAttemptMapper = taskStepAttemptMapper;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    public TaskDetailView detail(long userId, String taskId) {
        TaskRow row = taskMapper.findOwnedById(taskId, userId, localeTag());
        if (row == null) {
            throw ApiException.of(ErrorCode.TASK_NOT_FOUND);
        }
        List<TaskStepRow> steps = taskStepMapper.findByTask(taskId);
        Map<Long, List<TaskStepAttemptRow>> attempts = taskStepAttemptMapper.findByTask(taskId).stream()
                .collect(Collectors.groupingBy(TaskStepAttemptRow::getStepId,
                        LinkedHashMap::new, Collectors.toList()));
        List<TaskMaterialView> materials = taskMapper.findMaterials(taskId).stream()
                .map(material -> new TaskMaterialView(material.getSlotIndex(), material.getAssetId(),
                        "/api/assets/" + material.getAssetId() + "/content"))
                .toList();

        return new TaskDetailView(
                row.getId(),
                row.getTemplateId(),
                row.getDisplayTitle(),
                row.getTemplateCoverPath(),
                new CanvasView(row.getCanvasWidth(), row.getCanvasHeight()),
                row.getStatus().name(),
                row.getCurrentStage() == null ? null : row.getCurrentStage().name(),
                retry(row, currentAttemptCount(steps)),
                row.getResumeCount(),
                steps.stream().map(step -> stepView(row, step, attempts.getOrDefault(step.getId(), List.of()))).toList(),
                materials,
                failure(row),
                result(row, true),
                TimeFormat.toInstant(row.getCreatedAt()),
                TimeFormat.toInstant(row.getUpdatedAt()),
                TimeFormat.toInstant(row.getFinishedAt()));
    }

    public TaskPageView list(long userId, String status, String templateId, int page, int size) {
        validatePage(page, size);
        String statusFilter = null;
        boolean active = false;
        String normalizedStatus = status == null || status.isBlank() ? "all" : status;
        if ("all".equals(normalizedStatus)) {
            statusFilter = null;
        } else if ("active".equals(normalizedStatus)) {
            active = true;
        } else {
            try {
                statusFilter = TaskStatus.valueOf(normalizedStatus).name();
            } catch (IllegalArgumentException ex) {
                throw ApiException.validation(List.of(new FieldErrorView("status",
                        messages.get("validation.status.invalid"))));
            }
        }
        String templateFilter = templateId == null || templateId.isBlank() ? null : templateId;
        long total = taskMapper.countPage(userId, statusFilter, active, templateFilter);
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        List<TaskSummaryView> items = total == 0
                ? List.of()
                : taskMapper.findPage(userId, statusFilter, active, templateFilter,
                        localeTag(), (page - 1) * size, size).stream().map(this::summary).toList();
        return new TaskPageView(items, page, size, total, totalPages, page < totalPages);
    }

    private TaskSummaryView summary(TaskListRow row) {
        return new TaskSummaryView(
                row.getId(),
                row.getTemplateId(),
                row.getDisplayTitle(),
                row.getTemplateCoverPath(),
                row.getStatus().name(),
                row.getCurrentStage() == null ? null : row.getCurrentStage().name(),
                summaryRetry(row),
                row.getResumeCount(),
                summaryFailure(row),
                summaryResult(row),
                TimeFormat.toInstant(row.getCreatedAt()),
                TimeFormat.toInstant(row.getUpdatedAt()),
                TimeFormat.toInstant(row.getFinishedAt()));
    }

    private TaskRetryView summaryRetry(TaskListRow row) {
        boolean retrying = row.getStatus() == TaskStatus.RETRYING && row.getLastErrorCode() != null;
        TaskErrorView lastError = retrying
                ? new TaskErrorView(row.getLastErrorCode(),
                        failureMessage(row.getLastErrorCode(), Boolean.TRUE.equals(row.getLastErrorSimulated())),
                        Boolean.TRUE.equals(row.getLastErrorSimulated()))
                : null;
        return new TaskRetryView(row.getRetryCount(), row.getCurrentAttemptCount(), row.getMaxStepAttempts(),
                retrying ? TimeFormat.toInstant(row.getNextRetryAt()) : null, lastError);
    }

    private TaskFailureView summaryFailure(TaskListRow row) {
        if (row.getStatus() != TaskStatus.FAILED || row.getFailureCode() == null) {
            return null;
        }
        return new TaskFailureView(
                row.getCurrentStage() == null ? null : row.getCurrentStage().name(),
                row.getFailureCode(),
                failureMessage(row.getFailureCode(), row.isFailureSimulated()),
                row.isFailureSimulated());
    }

    private TaskResultView summaryResult(TaskListRow row) {
        if (row.getStatus() != TaskStatus.SUCCEEDED) {
            return null;
        }
        return resultView(row.getId(), row.getOutputKind(), row.getResultMimeType(),
                row.getResultPosterKey(), row.getResultWidth(), row.getResultHeight(),
                row.getResultDurationMs(), null);
    }

    private TaskRetryView retry(TaskRow row, int attemptCount) {
        boolean retrying = row.getStatus() == TaskStatus.RETRYING && row.getLastErrorCode() != null;
        TaskErrorView lastError = retrying
                ? new TaskErrorView(row.getLastErrorCode(),
                        failureMessage(row.getLastErrorCode(), Boolean.TRUE.equals(row.getLastErrorSimulated())),
                        Boolean.TRUE.equals(row.getLastErrorSimulated()))
                : null;
        return new TaskRetryView(row.getRetryCount(), attemptCount, row.getMaxStepAttempts(),
                retrying ? TimeFormat.toInstant(row.getNextRetryAt()) : null, lastError);
    }

    private TaskFailureView failure(TaskRow row) {
        if (row.getStatus() != TaskStatus.FAILED || row.getFailureCode() == null) {
            return null;
        }
        return new TaskFailureView(
                row.getCurrentStage() == null ? null : row.getCurrentStage().name(),
                row.getFailureCode(),
                failureMessage(row.getFailureCode(), row.isFailureSimulated()),
                row.isFailureSimulated());
    }

    private TaskResultView result(TaskRow row, boolean includeSize) {
        if (row.getStatus() != TaskStatus.SUCCEEDED) {
            return null;
        }
        return resultView(row.getId(), row.getOutputKind(), row.getResultMimeType(),
                row.getResultPosterKey(), row.getResultWidth(), row.getResultHeight(),
                row.getResultDurationMs(), includeSize ? row.getResultSizeBytes() : null);
    }

    private TaskResultView resultView(String taskId,
                                      TemplateKind outputKind,
                                      String mimeType,
                                      String posterKey,
                                      Integer width,
                                      Integer height,
                                      Integer durationMs,
                                      Long sizeBytes) {
        String kind = outputKind == null ? "IMAGE" : outputKind.name();
        String resolvedMime = mimeType == null ? "image/jpeg" : mimeType;
        String posterUrl = posterKey == null ? null : "/api/tasks/" + taskId + "/result/poster";
        return new TaskResultView(true, kind, resolvedMime, resultUrl(taskId), posterUrl,
                width, height, durationMs, sizeBytes);
    }

    private TaskStepView stepView(TaskRow task, TaskStepRow step, List<TaskStepAttemptRow> attempts) {
        return new TaskStepView(
                step.getSeq(),
                step.getStage().name(),
                step.getStatus().name(),
                step.getAttemptCount(),
                stepMessage(task, step),
                step.getErrorCode(),
                TimeFormat.toInstant(step.getStartedAt()),
                TimeFormat.toInstant(step.getFinishedAt()),
                attempts.stream().map(this::attemptView).toList());
    }

    private TaskAttemptView attemptView(TaskStepAttemptRow attempt) {
        String errorMessage = null;
        if (attempt.getStatus() == AttemptStatus.FAILED && attempt.getErrorCode() != null) {
            errorMessage = failureMessage(attempt.getErrorCode(), false);
        } else if (attempt.getStatus() == AttemptStatus.INTERRUPTED) {
            errorMessage = messages.get("task.attempt.INTERRUPTED");
        }
        return new TaskAttemptView(
                attempt.getAttemptNo(),
                attempt.getStatus().name(),
                attempt.getErrorCode(),
                errorMessage,
                TimeFormat.toInstant(attempt.getStartedAt()),
                TimeFormat.toInstant(attempt.getFinishedAt()));
    }

    private String stepMessage(TaskRow task, TaskStepRow step) {
        if (step.getStatus() == StepStatus.SUCCEEDED) {
            return messages.getNamed("task.step." + step.getStage().name() + ".succeeded",
                    arguments(step.getMessageArgs()));
        }
        if (step.getStatus() == StepStatus.FAILED && step.getErrorCode() != null) {
            boolean simulated = task.isFailureSimulated() || Boolean.TRUE.equals(task.getLastErrorSimulated());
            return failureMessage(step.getErrorCode(), simulated);
        }
        if (step.getStatus() == StepStatus.RUNNING) {
            return messages.get("task.step." + step.getStage().name() + ".running");
        }
        return null;
    }

    private String failureMessage(String code, boolean simulated) {
        String baseKey = "task.failure." + code;
        String key = simulated && messages.exists(baseKey + ".simulated") ? baseKey + ".simulated" : baseKey;
        return messages.get(key);
    }

    private Map<String, Object> arguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, ARG_MAP);
        } catch (Exception ex) {
            log.warn("task.message_args_invalid value={}", json);
            return Map.of();
        }
    }

    private int currentAttemptCount(List<TaskStepRow> steps) {
        for (int index = steps.size() - 1; index >= 0; index--) {
            TaskStepRow step = steps.get(index);
            if (step.getStatus() == StepStatus.RUNNING
                    || step.getStatus() == StepStatus.FAILED
                    || step.getStatus() == StepStatus.SUCCEEDED) {
                return step.getAttemptCount();
            }
        }
        return 0;
    }

    private void validatePage(int page, int size) {
        List<FieldErrorView> errors = new ArrayList<>();
        if (page < 1) {
            errors.add(new FieldErrorView("page", messages.get("validation.page.range")));
        }
        if (size < 1 || size > 50) {
            errors.add(new FieldErrorView("size", messages.get("validation.size.range")));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private String resultUrl(String taskId) {
        return "/api/tasks/" + taskId + "/result";
    }

    private String localeTag() {
        return LocaleSupport.tag(LocaleContextHolder.getLocale());
    }
}
