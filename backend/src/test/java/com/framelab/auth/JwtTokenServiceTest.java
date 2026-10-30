package com.framelab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.framelab.common.security.AuthUser;
import com.framelab.config.SecurityProperties;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String SECRET = "framelab-unit-test-secret-32-bytes!!";

    @Test
    void createsAndParsesAccessToken() {
        JwtTokenService service = service(SECRET, "framelab-api", "framelab-web", Clock.fixed(NOW, ZoneOffset.UTC));

        String token = service.createAccessToken(new AuthUser(1, "demo@framelab.local", "USER", 7));

        AuthUser parsed = service.parse(token);
        assertThat(parsed.id()).isEqualTo(1);
        assertThat(parsed.email()).isEqualTo("demo@framelab.local");
        assertThat(parsed.role()).isEqualTo("USER");
        assertThat(parsed.tokenVersion()).isEqualTo(7);
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsTamperedSignature() {
        JwtTokenService service = service(SECRET, "framelab-api", "framelab-web", Clock.fixed(NOW, ZoneOffset.UTC));
        String token = service.createAccessToken(new AuthUser(1, "demo@framelab.local", "USER", 0));
        String[] parts = token.split("\\.");
        char[] signature = parts[2].toCharArray();
        signature[0] = signature[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(TokenInvalidException.class);
    }

    @Test
    void rejectsWrongIssuerAudienceOrSecret() {
        JwtTokenService signer = service(SECRET, "framelab-api", "framelab-web", Clock.fixed(NOW, ZoneOffset.UTC));
        String token = signer.createAccessToken(new AuthUser(1, "demo@framelab.local", "USER", 0));

        JwtTokenService wrongIssuer = service(SECRET, "other-issuer", "framelab-web",
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> wrongIssuer.parse(token)).isInstanceOf(TokenInvalidException.class);

        JwtTokenService wrongAudience = service(SECRET, "framelab-api", "other-audience",
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> wrongAudience.parse(token)).isInstanceOf(TokenInvalidException.class);

        JwtTokenService wrongSecret = service("another-framelab-unit-secret-32-bytes", "framelab-api", "framelab-web",
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> wrongSecret.parse(token)).isInstanceOf(TokenInvalidException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenService signer = service(SECRET, "framelab-api", "framelab-web", Clock.fixed(NOW, ZoneOffset.UTC));
        String token = signer.createAccessToken(new AuthUser(1, "demo@framelab.local", "USER", 0));
        JwtTokenService later = service(SECRET, "framelab-api", "framelab-web",
                Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(TokenExpiredException.class);
    }

    private JwtTokenService service(String secret, String issuer, String audience, Clock clock) {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtSecret(secret);
        properties.setIssuer(issuer);
        properties.setAudience(audience);
        return new JwtTokenService(properties, clock);
    }
}
