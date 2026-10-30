package com.framelab.task;

import java.time.LocalDateTime;

import com.framelab.task.domain.TaskStage;
import com.framelab.task.domain.TaskStatus;
import com.framelab.template.TemplateKind;

public class TaskRow {

    private String id;
    private long userId;
    private String templateId;
    private TemplateKind templateKind;
    private String templateTitle;
    private String displayTitle;
    private String templateCoverPath;
    private String renderSpecSnapshot;
    private int canvasWidth;
    private int canvasHeight;
    private String clientRequestId;
    private TaskStatus status;
    private TaskStage currentStage;
    private TemplateKind outputKind;
    private int maxStepAttempts;
    private int retryCount;
    private int resumeCount;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private String lastErrorDetail;
    private Boolean lastErrorSimulated;
    private String failureCode;
    private String failureArgs;
    private String failureDetail;
    private boolean failureSimulated;
    private String resultStorageKey;
    private String resultMimeType;
    private String resultPosterKey;
    private Integer resultWidth;
    private Integer resultHeight;
    private Integer resultDurationMs;
    private Long resultSizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
    private int rowVersion;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public TemplateKind getTemplateKind() {
        return templateKind;
    }

    public void setTemplateKind(TemplateKind templateKind) {
        this.templateKind = templateKind;
    }

    public String getRenderSpecSnapshot() {
        return renderSpecSnapshot;
    }

    public void setRenderSpecSnapshot(String renderSpecSnapshot) {
        this.renderSpecSnapshot = renderSpecSnapshot;
    }

    public TemplateKind getOutputKind() {
        return outputKind;
    }

    public void setOutputKind(TemplateKind outputKind) {
        this.outputKind = outputKind;
    }

    public String getTemplateTitle() {
        return templateTitle;
    }

    public void setTemplateTitle(String templateTitle) {
        this.templateTitle = templateTitle;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public void setDisplayTitle(String displayTitle) {
        this.displayTitle = displayTitle;
    }

    public String getTemplateCoverPath() {
        return templateCoverPath;
    }

    public void setTemplateCoverPath(String templateCoverPath) {
        this.templateCoverPath = templateCoverPath;
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public void setCanvasWidth(int canvasWidth) {
        this.canvasWidth = canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public void setCanvasHeight(int canvasHeight) {
        this.canvasHeight = canvasHeight;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(TaskStage currentStage) {
        this.currentStage = currentStage;
    }

    public int getMaxStepAttempts() {
        return maxStepAttempts;
    }

    public void setMaxStepAttempts(int maxStepAttempts) {
        this.maxStepAttempts = maxStepAttempts;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getResumeCount() {
        return resumeCount;
    }

    public void setResumeCount(int resumeCount) {
        this.resumeCount = resumeCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorDetail() {
        return lastErrorDetail;
    }

    public void setLastErrorDetail(String lastErrorDetail) {
        this.lastErrorDetail = lastErrorDetail;
    }

    public Boolean getLastErrorSimulated() {
        return lastErrorSimulated;
    }

    public void setLastErrorSimulated(Boolean lastErrorSimulated) {
        this.lastErrorSimulated = lastErrorSimulated;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureArgs() {
        return failureArgs;
    }

    public void setFailureArgs(String failureArgs) {
        this.failureArgs = failureArgs;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
    }

    public boolean isFailureSimulated() {
        return failureSimulated;
    }

    public void setFailureSimulated(boolean failureSimulated) {
        this.failureSimulated = failureSimulated;
    }

    public String getResultStorageKey() {
        return resultStorageKey;
    }

    public void setResultStorageKey(String resultStorageKey) {
        this.resultStorageKey = resultStorageKey;
    }

    public String getResultMimeType() {
        return resultMimeType;
    }

    public void setResultMimeType(String resultMimeType) {
        this.resultMimeType = resultMimeType;
    }

    public String getResultPosterKey() {
        return resultPosterKey;
    }

    public void setResultPosterKey(String resultPosterKey) {
        this.resultPosterKey = resultPosterKey;
    }

    public Integer getResultDurationMs() {
        return resultDurationMs;
    }

    public void setResultDurationMs(Integer resultDurationMs) {
        this.resultDurationMs = resultDurationMs;
    }

    public Integer getResultWidth() {
        return resultWidth;
    }

    public void setResultWidth(Integer resultWidth) {
        this.resultWidth = resultWidth;
    }

    public Integer getResultHeight() {
        return resultHeight;
    }

    public void setResultHeight(Integer resultHeight) {
        this.resultHeight = resultHeight;
    }

    public Long getResultSizeBytes() {
        return resultSizeBytes;
    }

    public void setResultSizeBytes(Long resultSizeBytes) {
        this.resultSizeBytes = resultSizeBytes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }
}
