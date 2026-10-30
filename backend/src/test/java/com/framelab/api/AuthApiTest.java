package com.framelab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.framelab.auth.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends ApiIntegrationTestSupport {

    @Test
    void loginReturnsAccessTokenUserAndRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@framelab.local","password":"Demo1234!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("demo@framelab.local"))
                .andExpect(jsonPath("$.user.displayName").value("林小满"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.createdAt").value(containsString("Z")))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("framelab_rt=")
                .contains("Path=/api/auth")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=604800")
                .doesNotContain("Secure");

        String rawToken = refreshToken(result);
        assertThat(rawToken).isNotBlank().hasSize(43);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE user_id = 1 ORDER BY id DESC LIMIT 1", String.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(rawToken);

        String accessToken = json(result).path("accessToken").asText();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.displayName").value("林小满"));
    }

    @Test
    void loginFailureIsUnifiedForUnknownEmailAndWrongPassword() throws Exception {
        MvcResult unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@framelab.local","password":"Demo1234!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@framelab.local","password":"Wrong1234!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        assertThat(problemCode(unknown)).isEqualTo(problemCode(wrongPassword));
        assertThat(json(unknown).path("detail").asText()).isEqualTo(json(wrongPassword).path("detail").asText());
        assertThat(unknown.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(wrongPassword.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void refreshAndLogoutFollowSessionContract() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(1))
                .andReturn();
        assertThat(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("framelab_rt=;")
                .contains("Max-Age=0");

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        Integer revoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM refresh_token WHERE revoked_at IS NOT NULL", Integer.class);
        assertThat(revoked).isPositive();
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        jdbcTemplate.update("""
                        UPDATE refresh_token
                           SET created_at = TIMESTAMPADD(SECOND, -10, UTC_TIMESTAMP(3)),
                               expires_at = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(3))
                         WHERE token_hash = ?
                        """, RefreshTokenService.hash(login.refreshToken()));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET status = 'DISABLED' WHERE id = 1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@framelab.local","password":"Demo1234!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void logoutWithoutCookieIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void meRequiresValidAccessToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        LoginResult login = login("demo@framelab.local", "Demo1234!");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@framelab.local"));
    }

    @Test
    void loginIsRateLimitedAfterFiveFailures() throws Exception {
        String email = "ratelimit-" + System.nanoTime() + "@framelab.local";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", email, "password", "Wrong1234!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "Wrong1234!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void loginValidationIsLocalized() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("Enter your email.").contains("Enter your password.");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en-US");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void logoutClearCookieHasNoValue() throws Exception {
        LoginResult login = login("demo@framelab.local", "Demo1234!");
        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("framelab_rt=;")
                .contains("Max-Age=0");
    }
}
