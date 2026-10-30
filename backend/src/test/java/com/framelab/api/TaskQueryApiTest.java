package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class TaskQueryApiTest extends TaskTestSupport {

    @Test
    void listsOwnTasksWithFiltersPaginationAndIsolation() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        LoginResult other = login("demo2@framelab.local", "Demo1234!");
        String taskA = createTravelTask(login, new Color(20, 40, 60));
        String taskB = createTravelTask(login, new Color(60, 40, 20));
        String otherTask = submitTask(other, "travel-postcard",
                List.of(uploadJpeg(other, "travel-postcard", 50, 50, new Color(10, 10, 10)),
                        uploadJpeg(other, "travel-postcard", 50, 50, new Color(200, 200, 200))),
                newRequestId()).path("id").asText();

        JsonNode page = list(login, "?page=1&size=20");
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("page").asInt()).isEqualTo(1);
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("totalPages").asInt()).isEqualTo(1);
        assertThat(page.path("hasNext").asBoolean()).isFalse();
        assertThat(ids(page)).containsExactlyInAnyOrder(taskA, taskB);
        assertThat(ids(page)).doesNotContain(otherTask);

        assertThat(list(login, "?status=QUEUED").path("total").asLong()).isEqualTo(2);
        assertThat(list(login, "?status=active").path("total").asLong()).isEqualTo(2);
        assertThat(list(login, "?status=SUCCEEDED").path("total").asLong()).isZero();
        assertThat(list(login, "?templateId=travel-postcard").path("total").asLong()).isEqualTo(2);
        assertThat(list(login, "?templateId=film-memory").path("total").asLong()).isZero();

        JsonNode firstPage = list(login, "?page=1&size=1");
        assertThat(firstPage.path("total").asLong()).isEqualTo(2);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("items").size()).isEqualTo(1);
        JsonNode secondPage = list(login, "?page=2&size=1");
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.path("items").size()).isEqualTo(1);

        MvcResult invalidStatus = mockMvc.perform(get("/api/tasks")
                        .param("status", "DONE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(problemCode(invalidStatus)).isEqualTo("VALIDATION_FAILED");
        MvcResult invalidPage = mockMvc.perform(get("/api/tasks")
                        .param("page", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(problemCode(invalidPage)).isEqualTo("VALIDATION_FAILED");
        MvcResult invalidSize = mockMvc.perform(get("/api/tasks")
                        .param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(problemCode(invalidSize)).isEqualTo("VALIDATION_FAILED");

        assertThat(detailStatus(other, taskA)).isEqualTo(404);
        assertThat(resultStatus(other, taskA)).isEqualTo(404);
        assertThat(detailStatus(login, otherTask)).isEqualTo(404);

        MvcResult unauthenticated = mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(problemCode(unauthenticated)).isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void returnsLocalizedDetailAndDoesNotMutateOnRead() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String first = uploadJpeg(login, "travel-postcard", 110, 80, new Color(30, 140, 90));
        String second = uploadJpeg(login, "travel-postcard", 80, 110, new Color(140, 30, 90));
        String taskId = submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();

        zeroSimulationDelays();
        runQueuedTasks();

        JsonNode zh = taskDetail(login, taskId, "zh-CN");
        assertThat(zh.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(zh.path("templateTitle").asText()).isEqualTo("旅行明信片");
        assertThat(zh.path("steps").size()).isEqualTo(3);
        assertThat(zh.path("steps").get(0).path("message").asText()).isEqualTo("素材检查通过（2/2）");
        assertThat(zh.path("steps").get(1).path("message").asText()).isEqualTo("版式已确定：1600×1200");
        assertThat(zh.path("steps").get(2).path("message").asText()).isEqualTo("结果已保存");
        for (JsonNode step : zh.path("steps")) {
            assertThat(step.path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(step.path("attemptCount").asInt()).isEqualTo(1);
            assertThat(step.path("errorCode").isNull()).isTrue();
            assertThat(step.path("attempts").size()).isEqualTo(1);
            assertThat(step.path("attempts").get(0).path("status").asText()).isEqualTo("SUCCEEDED");
        }
        assertThat(zh.path("retry").path("attemptCount").asInt()).isEqualTo(1);
        assertThat(zh.path("failure").isNull()).isTrue();
        assertThat(zh.path("result").path("available").asBoolean()).isTrue();
        assertThat(zh.path("result").path("url").asText()).isEqualTo("/api/tasks/" + taskId + "/result");
        assertThat(zh.path("result").path("width").asInt()).isEqualTo(1600);
        assertThat(zh.path("result").path("height").asInt()).isEqualTo(1200);
        assertThat(zh.path("result").path("sizeBytes").asLong()).isPositive();

        MvcResult englishResult = mockMvc.perform(get("/api/tasks/" + taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(englishResult.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en-US");
        assertThat(englishResult.getResponse().getHeader(HttpHeaders.VARY)).contains("Accept-Language");
        JsonNode english = json(englishResult);
        assertThat(english.path("templateTitle").asText()).isEqualTo("Travel Postcard");
        assertThat(english.path("steps").get(0).path("message").asText()).isEqualTo("Materials checked (2/2)");
        assertThat(english.path("steps").get(1).path("message").asText()).isEqualTo("Layout ready: 1600×1200");
        assertThat(english.path("steps").get(2).path("message").asText()).isEqualTo("Result saved");

        LocalDateTime taskUpdated = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM task WHERE id = ?", LocalDateTime.class, taskId);
        List<LocalDateTime> stepTimes = jdbcTemplate.queryForList(
                "SELECT finished_at FROM task_step WHERE task_id = ? ORDER BY seq",
                LocalDateTime.class, taskId);
        int attempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id = ?", Integer.class, taskId);
        for (int index = 0; index < 3; index++) {
            taskDetail(login, taskId, "zh-CN");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM task WHERE id = ?", LocalDateTime.class, taskId))
                .isEqualTo(taskUpdated);
        assertThat(jdbcTemplate.queryForList(
                "SELECT finished_at FROM task_step WHERE task_id = ? ORDER BY seq",
                LocalDateTime.class, taskId)).isEqualTo(stepTimes);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_step_attempt WHERE task_id = ?", Integer.class, taskId))
                .isEqualTo(attempts);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE id = ?", String.class, taskId)).isEqualTo("SUCCEEDED");
    }

    @Test
    void rejectsResultBeforeSuccess() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        String taskId = createTravelTask(login, new Color(12, 34, 56));

        MvcResult notReady = mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(problemCode(notReady)).isEqualTo("TASK_NOT_READY");
    }

    private String createTravelTask(LoginResult login, Color color) throws Exception {
        String first = uploadJpeg(login, "travel-postcard", 64, 64, color);
        String second = uploadJpeg(login, "travel-postcard", 64, 64, color.brighter());
        return submitTask(login, "travel-postcard", List.of(first, second), newRequestId())
                .path("id").asText();
    }

    private JsonNode list(LoginResult login, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tasks" + query)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private Set<String> ids(JsonNode page) {
        Set<String> ids = new HashSet<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asText()));
        return ids;
    }

    private int detailStatus(LoginResult login, String taskId) throws Exception {
        return mockMvc.perform(get("/api/tasks/" + taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andReturn().getResponse().getStatus();
    }

    private int resultStatus(LoginResult login, String taskId) throws Exception {
        return mockMvc.perform(get("/api/tasks/" + taskId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andReturn().getResponse().getStatus();
    }
}
