package app.bolo.learner.application.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GuestRateLimiterTest {

    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    @Test
    void allowsUpToTheLimitPerKeyThenRefuses() {
        GuestRateLimiter limiter = new GuestRateLimiter(2, Duration.ofHours(1));
        assertThat(limiter.tryAcquire("a", T0)).isTrue();
        assertThat(limiter.tryAcquire("a", T0.plusSeconds(1))).isTrue();
        assertThat(limiter.tryAcquire("a", T0.plusSeconds(2))).isFalse();
        assertThat(limiter.tryAcquire("b", T0.plusSeconds(2))).isTrue();
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        GuestRateLimiter limiter = new GuestRateLimiter(1, Duration.ofHours(1));
        assertThat(limiter.tryAcquire("a", T0)).isTrue();
        assertThat(limiter.tryAcquire("a", T0.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(limiter.tryAcquire("a", T0.plus(Duration.ofMinutes(61)))).isTrue();
    }
}
