package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class TaskSubmissionApiTest extends TaskTestSupport {

    @Test
    void submitsQueuedTaskWithSnapshotStepsAndMaterials() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 120, 90, new Color(32, 96, 180));
        String second = uploadJpeg(login, "travel-postcard", 90, 120, new Color(180, 96, 32));
        String requestId = newRequestId();

        MvcResult result = submitRaw(login, "travel-postcard", List.of(first, second), requestId);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .matches("/api/tasks/[0-9a-f-]{36}");
        assertThat(result.getResponse().getHeader("Idempotent-Replay")).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(result.getResponse().getHeader(HttpHeaders.VARY)).contains("Accept-Language");

        JsonNode body = json(result);
        String taskId = body.path("id").asText();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/tasks/" + taskId);
        assertThat(body.path("status").asText()).isEqualTo("QUEUED");
        assertThat(body.path("currentStage").isNull()).isTrue();
        assertThat(body.path("templateId").asText()).isEqualTo("travel-postcard");
        assertThat(body.path("templateTitle").asText()).isEqualTo("旅行明信片");
        assertThat(body.path("templateCoverUrl").asText()).isEqualTo("/assets/templates/travel-postcard.webp");
        assertThat(body.path("canvas").path("width").asInt()).isEqualTo(1600);
        assertThat(body.path("canvas").path("height").asInt()).isEqualTo(1200);
        assertThat(body.path("retry").path("retryCount").asInt()).isZero();
        assertThat(body.path("retry").path("attemptCount").asInt()).isZero();
        assertThat(body.path("retry").path("maxAttempts").asInt()).isEqualTo(3);
        assertThat(body.path("retry").path("nextRetryAt").isNull()).isTrue();
        assertThat(body.path("retry").path("lastError").isNull()).isTrue();
        assertThat(body.path("failure").isNull()).isTrue();
        assertThat(body.path("result").isNull()).isTrue();
        assertThat(body.path("resumeCount").asInt()).isZero();

        JsonNode steps = body.path("steps");
        assertThat(steps.size()).isEqualTo(3);
        assertThat(steps.get(0).path("seq").asInt()).isEqualTo(1);
        assertThat(steps.get(0).path("stage").asText()).isEqualTo("MATERIAL_CHECK");
        assertThat(steps.get(1).path("stage").asText()).isEqualTo("GENERATE");
        assertThat(steps.get(2).path("stage").asText()).isEqualTo("FINALIZE");
        for (JsonNode step : steps) {
            assertThat(step.path("status").asText()).isEqualTo("PENDING");
            assertThat(step.path("attemptCount").asInt()).isZero();
            assertThat(step.path("message").isNull()).isTrue();
            assertThat(step.path("attempts").size()).isZero();
        }

        JsonNode materials = body.path("materials");
        assertThat(materials.size()).isEqualTo(2);
        assertThat(materials.get(0).path("slotIndex").asInt()).isEqualTo(1);
        assertThat(materials.get(0).path("assetId").asText()).isEqualTo(first);
        assertThat(materials.get(0).path("contentUrl").asText())
                .isEqualTo("/api/assets/" + first + "/content");
        assertThat(materials.get(1).path("slotIndex").asInt()).isEqualTo(2);
        assertThat(materials.get(1).path("assetId").asText()).isEqualTo(second);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step WHERE task_id = ? AND status = 'PENDING'",
                Integer.class, taskId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_material WHERE task_id = ?", Integer.class, taskId)).isEqualTo(2);
    }

    @Test
    void replaysIdempotentRequestBeforeTemplateValidation() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 80, 80, new Color(20, 120, 80));
        String second = uploadJpeg(login, "travel-postcard", 80, 80, new Color(120, 20, 80));
        String requestId = newRequestId();
        JsonNode created = submitTask(login, "travel-postcard", List.of(first, second), requestId);

        jdbcTemplate.update("UPDATE template SET is_published = 0 WHERE id = 'travel-postcard'");
        MvcResult replay = submitRaw(login, "travel-postcard", List.of(first, second), requestId);

        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");
        assertThat(json(replay).path("id").asText()).isEqualTo(created.path("id").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step", Integer.class)).isEqualTo(3);
    }

    @Test
    void rejectsSameRequestIdWithDifferentPayload() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 70, 70, new Color(40, 40, 160));
        String second = uploadJpeg(login, "travel-postcard", 70, 70, new Color(160, 40, 40));
        String requestId = newRequestId();
        submitTask(login, "travel-postcard", List.of(first, second), requestId);

        MvcResult reversed = submitRaw(login, "travel-postcard", List.of(second, first), requestId);
        assertThat(reversed.getResponse().getStatus()).isEqualTo(409);
        assertThat(reversed.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(problemCode(reversed)).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        MvcResult otherTemplate = submitRaw(login, "film-memory", List.of(first), requestId);
        assertThat(otherTemplate.getResponse().getStatus()).isEqualTo(409);
        assertThat(problemCode(otherTemplate)).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsCountDuplicatesAndMalformedRequestId() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 60, 60, new Color(90, 90, 90));
        String second = uploadJpeg(login, "travel-postcard", 60, 60, new Color(110, 110, 110));

        MvcResult shortList = submitRaw(login, "travel-postcard", List.of(first), newRequestId());
        assertThat(shortList.getResponse().getStatus()).isEqualTo(400);
        assertThat(problemCode(shortList)).isEqualTo("VALIDATION_FAILED");
        assertThat(json(shortList).path("errors").get(0).path("field").asText()).isEqualTo("assetIds");

        MvcResult duplicate = submitRaw(login, "travel-postcard", List.of(first, first), newRequestId());
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(400);
        assertThat(problemCode(duplicate)).isEqualTo("VALIDATION_FAILED");

        MvcResult malformedKey = submitRaw(login, "travel-postcard", List.of(first, second), "not-a-uuid");
        assertThat(malformedKey.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(malformedKey).path("errors").get(0).path("field").asText())
                .isEqualTo("clientRequestId");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isZero();
    }

    @Test
    void rejectsForeignAssetAndMissingTemplate() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        LoginResult other = login("demo2@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 60, 60, new Color(90, 90, 90));
        String second = uploadJpeg(login, "travel-postcard", 60, 60, new Color(110, 110, 110));
        String foreign = uploadJpeg(other, "travel-postcard", 60, 60, new Color(30, 30, 30));

        MvcResult missingTemplate = submitRaw(login, "missing-template", List.of(first, second), newRequestId());
        assertThat(missingTemplate.getResponse().getStatus()).isEqualTo(404);
        assertThat(problemCode(missingTemplate)).isEqualTo("TEMPLATE_NOT_FOUND");

        MvcResult foreignAsset = submitRaw(login, "travel-postcard", List.of(first, foreign), newRequestId());
        assertThat(foreignAsset.getResponse().getStatus()).isEqualTo(404);
        assertThat(problemCode(foreignAsset)).isEqualTo("ASSET_NOT_FOUND");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isZero();
    }

    @Test
    void rejectsMimeMismatch() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, null, 60, 60, new Color(90, 90, 90));
        String second = uploadJpeg(login, null, 60, 60, new Color(110, 110, 110));

        jdbcTemplate.update("UPDATE template SET accepted_mime_types = 'image/png' WHERE id = 'travel-postcard'");
        MvcResult mimeMismatch = submitRaw(login, "travel-postcard", List.of(first, second), newRequestId());
        assertThat(mimeMismatch.getResponse().getStatus()).isEqualTo(400);
        assertThat(problemCode(mimeMismatch)).isEqualTo("VALIDATION_FAILED");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isZero();
    }

    @Test
    void rejectsUnpublishedTemplate() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, null, 60, 60, new Color(90, 90, 90));
        String second = uploadJpeg(login, null, 60, 60, new Color(110, 110, 110));

        jdbcTemplate.update("UPDATE template SET is_published = 0 WHERE id = 'travel-postcard'");
        MvcResult unpublished = submitRaw(login, "travel-postcard", List.of(first, second), newRequestId());
        assertThat(unpublished.getResponse().getStatus()).isEqualTo(404);
        assertThat(problemCode(unpublished)).isEqualTo("TEMPLATE_NOT_FOUND");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM task", Integer.class)).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"travel-postcard\",\"assetIds\":[],\"clientRequestId\":\""
                                + newRequestId() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andReturn();
        assertThat(problemCode(result)).isEqualTo("AUTH_REQUIRED");
    }
}
