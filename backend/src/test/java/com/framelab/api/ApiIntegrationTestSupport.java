package com.framelab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framelab.auth.LoginRateLimiter;
import com.framelab.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiIntegrationTestSupport {

    protected static final String TEST_JWT_SECRET = "framelab-test-secret-change-me-32bytes";
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("framelab")
            .withUsername("framelab")
            .withPassword("framelab_pass");
    protected static final Path STORAGE_ROOT = createStorageRoot();

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                + "&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("framelab.storage.root", () -> STORAGE_ROOT.toString());
        registry.add("framelab.security.jwt-secret", () -> TEST_JWT_SECRET);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected LoginRateLimiter loginRateLimiter;

    @Autowired
    protected SecurityProperties securityProperties;

    @BeforeEach
    void clearRateLimiter() {
        loginRateLimiter.clearAll();
    }

    protected LoginResult login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json(result);
        return new LoginResult(body.path("accessToken").asText(), refreshToken(result), body.path("user"));
    }

    protected String bearer(LoginResult login) {
        return "Bearer " + login.accessToken();
    }

    protected Cookie refreshCookie(String value) {
        return new Cookie(securityProperties.getCookieName(), value);
    }

    protected String refreshToken(MvcResult result) {
        List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String prefix = securityProperties.getCookieName() + "=";
        for (String header : headers) {
            if (header.startsWith(prefix)) {
                String value = header.substring(prefix.length());
                int semicolon = value.indexOf(';');
                return semicolon >= 0 ? value.substring(0, semicolon) : value;
            }
        }
        return null;
    }

    protected JsonNode json(MvcResult result) throws IOException {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected String problemCode(MvcResult result) throws IOException {
        return json(result).path("code").asText();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("framelab-test-storage");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test storage directory", ex);
        }
    }

    protected record LoginResult(String accessToken, String refreshToken, JsonNode user) {
    }
}
