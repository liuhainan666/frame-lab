package com.framelab.template;

public class TemplateRow {

    private String id;
    private String title;
    private String description;
    private String categoryId;
    private String categoryName;
    private TemplateKind kind;
    private String coverPath;
    private int requiredImageCount;
    private String acceptedMimeTypes;
    private String renderSpec;
    private String effectTags;
    private TemplateKind outputKind;
    private long maxFileSizeBytes;
    private int canvasWidth;
    private int canvasHeight;
    private int sortOrder;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public TemplateKind getKind() {
        return kind;
    }

    public void setKind(TemplateKind kind) {
        this.kind = kind;
    }

    public String getRenderSpec() {
        return renderSpec;
    }

    public void setRenderSpec(String renderSpec) {
        this.renderSpec = renderSpec;
    }

    public String getEffectTags() {
        return effectTags;
    }

    public void setEffectTags(String effectTags) {
        this.effectTags = effectTags;
    }

    public TemplateKind getOutputKind() {
        return outputKind;
    }

    public void setOutputKind(TemplateKind outputKind) {
        this.outputKind = outputKind;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public int getRequiredImageCount() {
        return requiredImageCount;
    }

    public void setRequiredImageCount(int requiredImageCount) {
        this.requiredImageCount = requiredImageCount;
    }

    public String getAcceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    public void setAcceptedMimeTypes(String acceptedMimeTypes) {
        this.acceptedMimeTypes = acceptedMimeTypes;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
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

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
