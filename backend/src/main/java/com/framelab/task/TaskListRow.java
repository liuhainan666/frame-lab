package com.framelab.task;

import java.time.LocalDateTime;

import com.framelab.task.domain.TaskStage;
import com.framelab.task.domain.TaskStatus;
import com.framelab.template.TemplateKind;

public class TaskListRow {

    private String id;
    private String templateId;
    private TemplateKind outputKind;
    private String displayTitle;
    private String templateCoverPath;
    private TaskStatus status;
    private TaskStage currentStage;
    private String failureCode;
    private String failureArgs;
    private boolean failureSimulated;
    private int retryCount;
    private int maxStepAttempts;
    private int currentAttemptCount;
    private int resumeCount;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private Boolean lastErrorSimulated;
    private Integer resultWidth;
    private Integer resultHeight;
    private Integer resultDurationMs;
    private String resultMimeType;
    private String resultPosterKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public TemplateKind getOutputKind() {
        return outputKind;
    }

    public void setOutputKind(TemplateKind outputKind) {
        this.outputKind = outputKind;
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

    public boolean isFailureSimulated() {
        return failureSimulated;
    }

    public void setFailureSimulated(boolean failureSimulated) {
        this.failureSimulated = failureSimulated;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxStepAttempts() {
        return maxStepAttempts;
    }

    public void setMaxStepAttempts(int maxStepAttempts) {
        this.maxStepAttempts = maxStepAttempts;
    }

    public int getCurrentAttemptCount() {
        return currentAttemptCount;
    }

    public void setCurrentAttemptCount(int currentAttemptCount) {
        this.currentAttemptCount = currentAttemptCount;
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

    public Boolean getLastErrorSimulated() {
        return lastErrorSimulated;
    }

    public void setLastErrorSimulated(Boolean lastErrorSimulated) {
        this.lastErrorSimulated = lastErrorSimulated;
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

    public Integer getResultDurationMs() {
        return resultDurationMs;
    }

    public void setResultDurationMs(Integer resultDurationMs) {
        this.resultDurationMs = resultDurationMs;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
