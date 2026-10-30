package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SimulationApiTest extends ApiIntegrationTestSupport {

    @Test
    void adminReadsAndReplacesSimulationConfiguration() throws Exception {
        LoginResult admin = login("admin@framelab.local", "Admin1234!");

        MvcResult initial = mockMvc.perform(get("/api/admin/simulation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode defaults = json(initial);
        assertThat(defaults.path("delays").path("materialCheckMs").asInt()).isEqualTo(800);
        assertThat(defaults.path("delays").path("generateMs").asInt()).isEqualTo(3000);
        assertThat(defaults.path("delays").path("finalizeMs").asInt()).isEqualTo(1200);
        assertThat(defaults.path("failure").path("stage").isNull()).isTrue();
        assertThat(defaults.path("failure").path("remaining").asInt()).isZero();
        assertThat(defaults.path("failure").path("retryable").asBoolean()).isFalse();
        assertThat(defaults.path("updatedBy").asLong()).isEqualTo(3);

        MvcResult updated = putSimulation(admin, """
                {
                  "delays": {"materialCheckMs": 0, "generateMs": 5, "finalizeMs": 7},
                  "failure": {"stage": "GENERATE", "remaining": 2, "retryable": true}
                }
                """);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(updated);
        assertThat(body.path("delays").path("materialCheckMs").asInt()).isZero();
        assertThat(body.path("delays").path("generateMs").asInt()).isEqualTo(5);
        assertThat(body.path("delays").path("finalizeMs").asInt()).isEqualTo(7);
        assertThat(body.path("failure").path("stage").asText()).isEqualTo("GENERATE");
        assertThat(body.path("failure").path("remaining").asInt()).isEqualTo(2);
        assertThat(body.path("failure").path("retryable").asBoolean()).isTrue();
        assertThat(body.path("updatedBy").asLong()).isEqualTo(3);
        assertThat(body.path("updatedAt").isNull()).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT generate_delay_ms FROM simulation_setting WHERE id = 1", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fail_stage FROM simulation_setting WHERE id = 1", String.class)).isEqualTo("GENERATE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fail_times FROM simulation_setting WHERE id = 1", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fail_retryable FROM simulation_setting WHERE id = 1", Boolean.class)).isTrue();
    }

    @Test
    void userCannotAccessSimulationAdministration() throws Exception {
        LoginResult user = login("demo@framelab.local", "Demo1234!");

        MvcResult read = mockMvc.perform(get("/api/admin/simulation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(problemCode(read)).isEqualTo("FORBIDDEN");

        MvcResult write = mockMvc.perform(put("/api/admin/simulation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(problemCode(write)).isEqualTo("FORBIDDEN");
    }

    @Test
    void rejectsInvalidSimulationConfiguration() throws Exception {
        LoginResult admin = login("admin@framelab.local", "Admin1234!");
        List<String> invalidPayloads = List.of(
                """
                {"delays":{"materialCheckMs":-1,"generateMs":0,"finalizeMs":0},
                 "failure":{"stage":null,"remaining":0,"retryable":false}}
                """,
                """
                {"delays":{"materialCheckMs":0,"generateMs":60001,"finalizeMs":0},
                 "failure":{"stage":null,"remaining":0,"retryable":false}}
                """,
                """
                {"delays":{"materialCheckMs":0,"generateMs":0,"finalizeMs":0},
                 "failure":{"stage":null,"remaining":1,"retryable":false}}
                """,
                """
                {"delays":{"materialCheckMs":0,"generateMs":0,"finalizeMs":0},
                 "failure":{"stage":null,"remaining":0,"retryable":true}}
                """,
                """
                {"delays":{"materialCheckMs":0,"generateMs":0,"finalizeMs":0},
                 "failure":{"stage":"GENERATE","remaining":51,"retryable":true}}
                """,
                """
                {"delays":{"materialCheckMs":0,"generateMs":0,"finalizeMs":0},
                 "failure":{"stage":"EXPORT","remaining":1,"retryable":false}}
                """,
                """
                {"delays":{"materialCheckMs":0,"generateMs":0,"finalizeMs":0},"failure":null}
                """);

        for (String payload : invalidPayloads) {
            MvcResult result = putSimulation(admin, payload);
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            assertThat(problemCode(result)).isEqualTo("VALIDATION_FAILED");
            assertThat(json(result).path("errors").size()).isPositive();
        }
    }

    private MvcResult putSimulation(LoginResult login, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/simulation")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String validPayload() {
        return """
                {"delays":{"materialCheckMs":0,"generateMs":0,"finalizeMs":0},
                 "failure":{"stage":null,"remaining":0,"retryable":false}}
                """;
    }
}
