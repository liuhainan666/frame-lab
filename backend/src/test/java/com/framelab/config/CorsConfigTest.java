package com.framelab.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void devPatternsAcceptAnyLocalhostPortWithCredentials() {
        SecurityProperties properties = new SecurityProperties();
        properties.setCorsAllowedOrigins(List.of("http://localhost:3000"));
        properties.setCorsAllowedOriginPatterns(List.of(
                "http://localhost:*", "http://127.0.0.1:*"));

        CorsConfiguration configuration = configuration(properties);

        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.checkOrigin("http://localhost:3001"))
                .isEqualTo("http://localhost:3001");
        assertThat(configuration.checkOrigin("http://127.0.0.1:4173"))
                .isEqualTo("http://127.0.0.1:4173");
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
    }

    @Test
    void exactOriginsStillApplyWithoutPatterns() {
        SecurityProperties properties = new SecurityProperties();
        properties.setCorsAllowedOrigins(List.of("http://localhost:3000"));

        CorsConfiguration configuration = configuration(properties);

        assertThat(configuration.checkOrigin("http://localhost:3000"))
                .isEqualTo("http://localhost:3000");
        assertThat(configuration.checkOrigin("http://localhost:3001")).isNull();
    }

    private CorsConfiguration configuration(SecurityProperties properties) {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(properties);
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/auth/login"));
    }
}
