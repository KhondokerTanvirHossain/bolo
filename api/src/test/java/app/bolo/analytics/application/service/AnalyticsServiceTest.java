package app.bolo.analytics.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.talk.application.port.in.TalkEnded;
import app.bolo.talk.application.port.in.TalkStarted;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class AnalyticsServiceTest {

    /** Review I3: analytics runs inside the talk's own path (and before the Coach's crisis clean-up); it must never throw. */
    @Test
    void aFailingInsertNeverBreaksTheTalk() {
        BoloClock clock = new BoloClock(java.time.Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), java.time.ZoneOffset.UTC));
        var service = new AnalyticsService((learner, name, props, at) -> { throw new IllegalStateException("db down"); }, clock);
        var learner = new LearnerId(UUID.randomUUID());
        assertThatCode(() -> service.onTalkStarted(new TalkStarted(TalkSessionId.newId(), learner))).doesNotThrowAnyException();
        assertThatCode(() -> service.onTalkEnded(new TalkEnded(TalkSessionId.newId(), learner, 3, "crisis"))).doesNotThrowAnyException();
    }
}
