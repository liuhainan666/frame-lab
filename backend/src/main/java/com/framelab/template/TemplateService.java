package com.framelab.template;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.i18n.LocaleSupport;
import com.framelab.template.dto.CanvasView;
import com.framelab.template.dto.CategoryListView;
import com.framelab.template.dto.CategoryView;
import com.framelab.template.dto.TemplateCategoryView;
import com.framelab.template.dto.TemplateDetailView;
import com.framelab.template.dto.TemplateListView;
import com.framelab.template.dto.TemplateOutputView;
import com.framelab.template.dto.TemplateSummaryView;
import com.framelab.template.spec.TemplateRenderSpec;
import com.framelab.template.spec.TemplateRenderSpecValidator;
import com.framelab.template.spec.TemplateSpecException;
import com.framelab.template.spec.VideoRenderSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final Pattern EFFECT_TAG = Pattern.compile("[a-z][a-z0-9-]{0,23}");

    private final TemplateMapper templateMapper;
    private final TemplateRenderSpecValidator specValidator;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateMapper templateMapper,
                           TemplateRenderSpecValidator specValidator,
                           ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.specValidator = specValidator;
        this.objectMapper = objectMapper;
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
        TemplateRenderSpec spec = requireRenderSpec(row);
        return new TemplateDetailView(
                row.getId(),
                row.getTitle(),
                kindOf(row),
                new TemplateCategoryView(row.getCategoryId(), row.getCategoryName()),
                row.getCoverPath(),
                row.getDescription(),
                row.getRequiredImageCount(),
                new CanvasView(row.getCanvasWidth(), row.getCanvasHeight()),
                mimeTypes(row.getAcceptedMimeTypes()),
                row.getMaxFileSizeBytes(),
                effectTags(row.getEffectTags()),
                outputView(row, spec));
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

    public TemplateRenderSpec requireRenderSpec(TemplateRow row) {
        TemplateKind kind = kindOf(row);
        if (row.getRenderSpec() == null || row.getRenderSpec().isBlank()) {
            if (kind == TemplateKind.VIDEO) {
                throw invalidSpec(row, "video template is missing render spec");
            }
            return null;
        }
        try {
            return specValidator.validate(row.getRenderSpec(), kind);
        } catch (TemplateSpecException ex) {
            throw invalidSpec(row, ex.getMessage());
        }
    }

    private TemplateSummaryView toSummary(TemplateRow row) {
        TemplateRenderSpec spec = requireRenderSpec(row);
        return new TemplateSummaryView(
                row.getId(),
                row.getTitle(),
                kindOf(row),
                new TemplateCategoryView(row.getCategoryId(), row.getCategoryName()),
                row.getCoverPath(),
                row.getDescription(),
                row.getRequiredImageCount(),
                new CanvasView(row.getCanvasWidth(), row.getCanvasHeight()),
                effectTags(row.getEffectTags()),
                outputView(row, spec));
    }

    private TemplateOutputView outputView(TemplateRow row, TemplateRenderSpec spec) {
        if (spec instanceof VideoRenderSpec video) {
            return new TemplateOutputView(TemplateKind.VIDEO.name(), video.durationMs(),
                    video.resolution().width(), video.resolution().height(), video.audio() != null);
        }
        return new TemplateOutputView(TemplateKind.IMAGE.name(), null,
                row.getCanvasWidth(), row.getCanvasHeight(), false);
    }

    private TemplateKind kindOf(TemplateRow row) {
        return row.getKind() == null ? TemplateKind.IMAGE : row.getKind();
    }

    private List<String> effectTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST).stream()
                    .filter(tag -> tag != null && EFFECT_TAG.matcher(tag).matches())
                    .distinct()
                    .limit(6)
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("template.effect_tags_invalid value={}", json);
            return List.of();
        }
    }

    private ApiException invalidSpec(TemplateRow row, String reason) {
        log.error("template.spec_invalid templateId={} reason={}", row.getId(), reason);
        return ApiException.of(ErrorCode.TEMPLATE_SPEC_INVALID);
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
