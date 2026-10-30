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
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[0].id").value("travel-postcard"))
                .andExpect(jsonPath("$.items[0].title").value("旅行明信片"))
                .andExpect(jsonPath("$.items[0].category.id").value("travel"))
                .andExpect(jsonPath("$.items[0].category.name").value("旅行风景"))
                .andExpect(jsonPath("$.items[0].coverUrl").value("/assets/templates/travel-postcard.webp"))
                .andExpect(jsonPath("$.items[0].requiredImageCount").value(2))
                .andExpect(jsonPath("$.items[0].canvas.width").value(1600))
                .andExpect(jsonPath("$.items[0].canvas.height").value(1200))
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
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value("travel-postcard"))
                .andExpect(jsonPath("$.items[1].id").value("summer-coast"));

        mockMvc.perform(get("/api/templates").param("category", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));

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
                .andExpect(jsonPath("$.total").value(9));

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
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=60")));
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
