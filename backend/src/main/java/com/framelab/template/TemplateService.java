package com.framelab.template;

import java.util.Arrays;
import java.util.List;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.i18n.LocaleSupport;
import com.framelab.template.dto.CanvasView;
import com.framelab.template.dto.CategoryListView;
import com.framelab.template.dto.CategoryView;
import com.framelab.template.dto.TemplateCategoryView;
import com.framelab.template.dto.TemplateDetailView;
import com.framelab.template.dto.TemplateListView;
import com.framelab.template.dto.TemplateSummaryView;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {

    private final TemplateMapper templateMapper;

    public TemplateService(TemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public CategoryListView categories() {
        List<CategoryView> items = templateMapper.findCategories(localeTag()).stream()
                .map(row -> new CategoryView(row.getId(), row.getName(), row.getSortOrder()))
                .toList();
        return new CategoryListView(items);
    }

    public TemplateListView list(String category) {
        String categoryId = category == null || category.isBlank() || "all".equals(category) ? null : category;
        List<TemplateSummaryView> items = templateMapper.findPublished(categoryId, localeTag()).stream()
                .map(this::toSummary)
                .toList();
        return new TemplateListView(items, items.size());
    }

    public TemplateDetailView detail(String templateId) {
        TemplateRow row = templateMapper.findPublishedById(templateId, localeTag());
        if (row == null) {
            throw ApiException.of(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        return new TemplateDetailView(
                row.getId(),
                row.getTitle(),
                new TemplateCategoryView(row.getCategoryId(), row.getCategoryName()),
                row.getCoverPath(),
                row.getDescription(),
                row.getRequiredImageCount(),
                new CanvasView(row.getCanvasWidth(), row.getCanvasHeight()),
                mimeTypes(row.getAcceptedMimeTypes()),
                row.getMaxFileSizeBytes());
    }

    public TemplateRow requirePublished(String templateId) {
        TemplateRow row = templateMapper.findPublishedById(templateId, LocaleSupport.ZH_CN_TAG);
        if (row == null) {
            throw ApiException.of(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        return row;
    }

    public TemplateLimits limits(String templateId) {
        TemplateRow row = templateMapper.findPublishedById(templateId, LocaleSupport.ZH_CN_TAG);
        if (row == null) {
            throw ApiException.of(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        return new TemplateLimits(row.getId(), mimeTypes(row.getAcceptedMimeTypes()), row.getMaxFileSizeBytes());
    }

    private TemplateSummaryView toSummary(TemplateRow row) {
        return new TemplateSummaryView(
                row.getId(),
                row.getTitle(),
                new TemplateCategoryView(row.getCategoryId(), row.getCategoryName()),
                row.getCoverPath(),
                row.getDescription(),
                row.getRequiredImageCount(),
                new CanvasView(row.getCanvasWidth(), row.getCanvasHeight()));
    }

    private List<String> mimeTypes(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String localeTag() {
        return LocaleSupport.tag(LocaleContextHolder.getLocale());
    }
}
