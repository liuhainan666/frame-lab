package com.framelab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.framelab.common.error.RateLimitException;
import com.framelab.config.SecurityProperties;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void blocksAfterFiveFailuresAndResetsOnSuccess() {
        MutableClock clock = new MutableClock(NOW);
        SecurityProperties properties = new SecurityProperties();
        properties.setLoginMaxFailures(5);
        properties.setLoginWindow(Duration.ofMinutes(15));
        LoginRateLimiter limiter = new LoginRateLimiter(properties, clock);

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("demo@framelab.local", "127.0.0.1");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("demo@framelab.local", "127.0.0.1"))
                .isInstanceOfSatisfying(RateLimitException.class,
                        ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(900));

        limiter.reset("demo@framelab.local", "127.0.0.1");
        assertThatCode(() -> limiter.checkAllowed("demo@framelab.local", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void windowExpiryClearsFailures() {
        MutableClock clock = new MutableClock(NOW);
        SecurityProperties properties = new SecurityProperties();
        properties.setLoginMaxFailures(5);
        properties.setLoginWindow(Duration.ofMinutes(15));
        LoginRateLimiter limiter = new LoginRateLimiter(properties, clock);

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("demo@framelab.local", "10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed("demo@framelab.local", "10.0.0.1"))
                .doesNotThrowAnyException();
        limiter.recordFailure("demo@framelab.local", "10.0.0.1");
        assertThatCode(() -> limiter.checkAllowed("demo@framelab.local", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
