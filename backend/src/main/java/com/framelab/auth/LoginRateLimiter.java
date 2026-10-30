package com.framelab.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.framelab.common.error.RateLimitException;
import com.framelab.config.SecurityProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final SecurityProperties properties;
    private final Clock clock;
    private final Cache<String, Attempt> attempts;

    public LoginRateLimiter(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(properties.getLoginWindow())
                .maximumSize(100_000)
                .build();
    }

    public void checkAllowed(String email, String ip) {
        Attempt attempt = attempts.getIfPresent(key(email, ip));
        if (attempt == null) {
            return;
        }
        Instant now = clock.instant();
        if (expired(attempt, now)) {
            attempts.invalidate(key(email, ip));
            return;
        }
        if (attempt.failures() >= properties.getLoginMaxFailures()) {
            long retryAfter = Duration.between(now, attempt.windowStart().plus(properties.getLoginWindow())).toSeconds();
            throw new RateLimitException(Math.max(1, retryAfter));
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        attempts.asMap().compute(key(email, ip), (ignored, existing) -> {
            if (existing == null || expired(existing, now)) {
                return new Attempt(now, 1);
            }
            return new Attempt(existing.windowStart(), existing.failures() + 1);
        });
    }

    public void reset(String email, String ip) {
        attempts.invalidate(key(email, ip));
    }

    public void clearAll() {
        attempts.invalidateAll();
    }

    private boolean expired(Attempt attempt, Instant now) {
        return !Duration.between(attempt.windowStart(), now).minus(properties.getLoginWindow()).isNegative();
    }

    private String key(String email, String ip) {
        return email.toLowerCase(Locale.ROOT) + "|" + (ip == null ? "unknown" : ip);
    }

    private record Attempt(Instant windowStart, int failures) {
    }
}
