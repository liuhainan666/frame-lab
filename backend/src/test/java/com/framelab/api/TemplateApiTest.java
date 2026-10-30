package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class TemplateApiTest extends ApiIntegrationTestSupport {

    @Test
    void listsPublishedTemplatesInStableOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(13))
                .andExpect(jsonPath("$.items", hasSize(13)))
                .andExpect(jsonPath("$.items[0].id").value("travel-postcard"))
                .andExpect(jsonPath("$.items[0].title").value("旅行明信片"))
                .andExpect(jsonPath("$.items[0].category.id").value("travel"))
                .andExpect(jsonPath("$.items[0].category.name").value("旅行风景"))
                .andExpect(jsonPath("$.items[0].coverUrl").value("/assets/templates/travel-postcard.webp"))
                .andExpect(jsonPath("$.items[0].requiredImageCount").value(2))
                .andExpect(jsonPath("$.items[0].canvas.width").value(1600))
                .andExpect(jsonPath("$.items[0].canvas.height").value(1200))
                .andExpect(jsonPath("$.items[0].kind").value("IMAGE"))
                .andExpect(jsonPath("$.items[0].effectTags", hasSize(2)))
                .andExpect(jsonPath("$.items[0].effectTags[0]").value("travel"))
                .andExpect(jsonPath("$.items[0].effectTags[1]").value("warm"))
                .andExpect(jsonPath("$.items[0].output.kind").value("IMAGE"))
                .andExpect(jsonPath("$.items[0].output.durationMs").doesNotExist())
                .andExpect(jsonPath("$.items[0].output.hasAudio").value(false))
                .andExpect(jsonPath("$.items[0].description").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("zh-CN");
        assertThat(String.join(",", result.getResponse().getHeaders(HttpHeaders.VARY)))
                .contains("Accept-Language");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("public").contains("max-age=60");
    }

    @Test
    void filtersByCategoryAndTreatsUnknownCategoryAsEmpty() throws Exception {
        mockMvc.perform(get("/api/templates").param("category", "travel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].id").value("travel-postcard"))
                .andExpect(jsonPath("$.items[1].id").value("golden-hour"))
                .andExpect(jsonPath("$.items[2].id").value("summer-coast"))
                .andExpect(jsonPath("$.items[3].id").value("city-walk-15s"))
                .andExpect(jsonPath("$.items[3].kind").value("VIDEO"))
                .andExpect(jsonPath("$.items[3].output.durationMs").value(15000));

        mockMvc.perform(get("/api/templates").param("category", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(13));

        mockMvc.perform(get("/api/templates").param("category", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void hidesUnpublishedTemplates() throws Exception {
        jdbcTemplate.update("UPDATE template SET is_published = 0 WHERE id = 'film-memory'");

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12));

        mockMvc.perform(get("/api/templates/film-memory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void returnsTemplateDetailWithLimits() throws Exception {
        mockMvc.perform(get("/api/templates/travel-postcard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("travel-postcard"))
                .andExpect(jsonPath("$.title").value("旅行明信片"))
                .andExpect(jsonPath("$.acceptedMimeTypes", hasSize(2)))
                .andExpect(jsonPath("$.acceptedMimeTypes[0]").value("image/jpeg"))
                .andExpect(jsonPath("$.acceptedMimeTypes[1]").value("image/png"))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(5242880))
                .andExpect(jsonPath("$.kind").value("IMAGE"))
                .andExpect(jsonPath("$.output.kind").value("IMAGE"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=60")));
    }

    @Test
    void returnsVideoTemplateKindOutputAndEffectTags() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO template
                  (id, category_id, kind, title, description, cover_path, render_spec, effect_tags,
                   output_kind, required_image_count, accepted_mime_types, max_file_size_bytes,
                   canvas_width, canvas_height, sort_order, is_published)
                VALUES ('test-video', 'travel', 'VIDEO', '测试视频', '测试视频模板',
                        '/assets/templates/travel-postcard.webp', ?, '["warm","cinematic"]', 'VIDEO',
                        3, 'image/jpeg,image/png', 10485760, 1080, 1920, 5, 1)
                """, """
                {
                  "version": 1,
                  "kind": "VIDEO",
                  "durationMs": 15000,
                  "fps": 30,
                  "resolution": { "width": 1080, "height": 1920 },
                  "slots": [
                    { "seq": 1, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "zoom-in" },
                    { "seq": 2, "durationMs": 3400, "transition": "slide-left", "transitionMs": 500, "motion": "pan-right" },
                    { "seq": 3, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "zoom-out" },
                    { "seq": 4, "durationMs": 3400, "transition": "slide-left", "transitionMs": 500, "motion": "zoom-in" },
                    { "seq": 5, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "none" }
                  ],
                  "look": { "preset": "city-warm", "lut": "luts/city-warm-v1.cube" },
                  "audio": { "path": "audio/city-walk-v1.mp3", "volume": 0.8, "fadeOutMs": 800 },
                  "text": [],
                  "watermark": true
                }
                """);

        mockMvc.perform(get("/api/templates/test-video"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("VIDEO"))
                .andExpect(jsonPath("$.effectTags", hasSize(2)))
                .andExpect(jsonPath("$.effectTags[0]").value("warm"))
                .andExpect(jsonPath("$.effectTags[1]").value("cinematic"))
                .andExpect(jsonPath("$.output.kind").value("VIDEO"))
                .andExpect(jsonPath("$.output.durationMs").value(15000))
                .andExpect(jsonPath("$.output.width").value(1080))
                .andExpect(jsonPath("$.output.height").value(1920))
                .andExpect(jsonPath("$.output.hasAudio").value(true));
    }

    @Test
    void rejectsBrokenTemplateSpec() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO template
                  (id, category_id, kind, title, description, cover_path, render_spec,
                   output_kind, required_image_count, accepted_mime_types, max_file_size_bytes,
                   canvas_width, canvas_height, sort_order, is_published)
                VALUES ('broken-spec', 'travel', 'IMAGE', '损坏模板', '用于校验渲染配置',
                        '/assets/templates/travel-postcard.webp', '{"version":1,"kind":"IMAGE"}', 'IMAGE',
                        1, 'image/jpeg,image/png', 10485760, 1200, 1600, 6, 1)
                """);

        mockMvc.perform(get("/api/templates/broken-spec"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TEMPLATE_SPEC_INVALID"));
    }

    @Test
    void returnsNotFoundForUnknownTemplate() throws Exception {
        mockMvc.perform(get("/api/templates/unknown-template"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void localizesTemplatesToEnglish() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/templates")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("travel-postcard"))
                .andExpect(jsonPath("$.items[0].title").value("Travel Postcard"))
                .andExpect(jsonPath("$.items[0].description")
                        .value("Turn two travel photos into a postcard you can send."))
                .andExpect(jsonPath("$.items[0].category.name").value("Travel & Landscape"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en-US");
    }

    @Test
    void acceptsPlainEnglishLanguageTag() throws Exception {
        mockMvc.perform(get("/api/templates").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en-US"))
                .andExpect(jsonPath("$.items[0].title").value("Travel Postcard"));
    }

    @Test
    void fallsBackToChineseWhenTranslationIsMissing() throws Exception {
        jdbcTemplate.update("DELETE FROM template_translation WHERE template_id = 'travel-postcard' AND locale = 'en-US'");

        mockMvc.perform(get("/api/templates/travel-postcard")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("旅行明信片"))
                .andExpect(jsonPath("$.description").value("把两张旅途照片拼成一张可以寄出的明信片。"));
    }

    @Test
    void servesTemplateCoversWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/assets/templates/travel-postcard.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"));
    }

    @Test
    void listsCategoriesWithoutAllEntry() throws Exception {
        mockMvc.perform(get("/api/template-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[0].id").value("travel"))
                .andExpect(jsonPath("$.items[0].name").value("旅行风景"))
                .andExpect(jsonPath("$.items[0].sortOrder").value(10));

        mockMvc.perform(get("/api/template-categories")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Travel & Landscape"));
    }
}
