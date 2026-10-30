package com.framelab.task;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.framelab.asset.AssetService;
import com.framelab.asset.UploadAsset;
import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.error.FieldErrorView;
import com.framelab.common.i18n.MessageResolver;
import com.framelab.task.dto.TaskSubmitRequest;
import com.framelab.template.TemplateRow;
import com.framelab.template.TemplateService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TaskSubmissionService {

    private final TaskMapper taskMapper;
    private final TaskCreator taskCreator;
    private final TaskQueryService taskQueryService;
    private final TemplateService templateService;
    private final AssetService assetService;
    private final MessageResolver messages;

    public TaskSubmissionService(TaskMapper taskMapper,
                                 TaskCreator taskCreator,
                                 TaskQueryService taskQueryService,
                                 TemplateService templateService,
                                 AssetService assetService,
                                 MessageResolver messages) {
        this.taskMapper = taskMapper;
        this.taskCreator = taskCreator;
        this.taskQueryService = taskQueryService;
        this.templateService = templateService;
        this.assetService = assetService;
        this.messages = messages;
    }

    public TaskSubmissionOutcome submit(long userId, TaskSubmitRequest request) {
        if (request == null) {
            throw validation("clientRequestId", messages.get("validation.clientRequestId.required"));
        }
        String clientRequestId = canonicalUuid(request.clientRequestId(), "clientRequestId");

        TaskRow existing = taskMapper.findByClientRequestId(userId, clientRequestId);
        if (existing != null) {
            return replayOrConflict(userId, existing, request);
        }

        String templateId = requireText(request.templateId(), "templateId",
                messages.get("validation.templateId.required"));
        List<String> assetIds = validateAssetIds(request.assetIds());
        TemplateRow template = templateService.requirePublished(templateId);

        if (assetIds.size() != template.getRequiredImageCount()) {
            throw validation("assetIds",
                    messages.get("validation.assetIds.count", template.getRequiredImageCount()));
        }
        if (new LinkedHashSet<>(assetIds).size() != assetIds.size()) {
            throw validation("assetIds", messages.get("validation.assetIds.duplicate"));
        }

        List<String> allowedMimeTypes = allowedMimeTypes(template.getAcceptedMimeTypes());
        List<UploadAsset> assets = new ArrayList<>();
        for (String assetId : assetIds) {
            UploadAsset asset = assetService.requireOwned(userId, assetId);
            assets.add(asset);
        }
        for (UploadAsset asset : assets) {
            if (!allowedMimeTypes.contains(asset.getMimeType())
                    || asset.getSizeBytes() > template.getMaxFileSizeBytes()) {
                throw validation("assetIds", messages.get("validation.assetIds.notAllowed"));
            }
        }

        try {
            String taskId = taskCreator.create(userId, template, assetIds, clientRequestId);
            return new TaskSubmissionOutcome(taskQueryService.detail(userId, taskId), false);
        } catch (DuplicateKeyException ex) {
            TaskRow concurrent = taskMapper.findByClientRequestId(userId, clientRequestId);
            if (concurrent == null) {
                throw ApiException.of(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return replayOrConflict(userId, concurrent, request);
        }
    }

    private TaskSubmissionOutcome replayOrConflict(long userId, TaskRow existing, TaskSubmitRequest request) {
        List<String> requestedAssetIds = request.assetIds() == null ? List.of() : request.assetIds();
        List<String> storedAssetIds = taskMapper.findMaterialAssetIds(existing.getId());
        boolean sameRequest = Objects.equals(existing.getTemplateId(), request.templateId())
                && storedAssetIds.equals(requestedAssetIds);
        if (!sameRequest) {
            throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return new TaskSubmissionOutcome(taskQueryService.detail(userId, existing.getId()), true);
    }

    private List<String> validateAssetIds(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw validation("assetIds", messages.get("validation.assetIds.required"));
        }
        List<String> canonical = new ArrayList<>(assetIds.size());
        for (String assetId : assetIds) {
            canonical.add(canonicalUuid(assetId, "assetIds"));
        }
        return canonical;
    }

    private String canonicalUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            String message = "clientRequestId".equals(field)
                    ? messages.get("validation.clientRequestId.required")
                    : messages.get("validation.assetIds.format");
            throw validation(field, message);
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException ex) {
            String message = "clientRequestId".equals(field)
                    ? messages.get("validation.clientRequestId.format")
                    : messages.get("validation.assetIds.format");
            throw validation(field, message);
        }
    }

    private String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw validation(field, message);
        }
        return value.trim();
    }

    private List<String> allowedMimeTypes(String csv) {
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private ApiException validation(String field, String message) {
        return ApiException.validation(List.of(new FieldErrorView(field, message)));
    }
}
