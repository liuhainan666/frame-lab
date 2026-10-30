package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ErrorI18nApiTest extends ApiIntegrationTestSupport {

    @Test
    void notFoundProblemIsLocalizedWithTraceHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/templates/not-here")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Template not found"))
                .andExpect(jsonPath("$.detail").value("The template does not exist or is no longer available."))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/templates/not-here"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en-US");
        assertThat(String.join(",", result.getResponse().getHeaders(HttpHeaders.VARY)))
                .contains("Accept-Language");
        String traceId = json(result).path("traceId").asText();
        assertThat(traceId).matches("[0-9a-f]{16}");
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo(traceId);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("Exception")
                .doesNotContain("java.");
    }

    @Test
    void unsupportedLanguageFallsBackToChinese() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/templates/not-here")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("模板不存在"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("zh-CN");
    }

    @Test
    void unauthenticatedAndInvalidTokenUseProblemJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void malformedJsonAndWrongMethodAreMapped() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        LoginResult login = login("demo@framelab.local", "Demo1234!");
        mockMvc.perform(put("/api/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(get("/api/unknown-path")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validationErrorsAreLocalizedPerField() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {"email":"not-an-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andReturn();

        JsonNode errors = json(result).path("errors");
        Map<String, String> byField = new HashMap<>();
        errors.forEach(error -> byField.put(error.path("field").asText(), error.path("message").asText()));
        assertThat(byField).containsEntry("email", "Enter a valid email address.")
                .containsEntry("password", "Enter your password.");
    }

    @Test
    void corsPreflightAllowsLanguageAndBearerHeaders() throws Exception {
        mockMvc.perform(options("/api/templates")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Accept-Language"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        allOf(containsString("Accept-Language"), containsString("Authorization"))))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        allOf(containsString("Content-Language"), containsString("Idempotent-Replay"),
                                containsString("Retry-After"))));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void acceptsClientTraceIdWhenSafe() throws Exception {
        mockMvc.perform(get("/api/templates/not-here").header("X-Request-Id", "client-trace-42"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "client-trace-42"))
                .andExpect(jsonPath("$.traceId").value("client-trace-42"));
    }
}
