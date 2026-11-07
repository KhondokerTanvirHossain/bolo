package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.out.ProfilePort;
import app.bolo.learner.domain.FearedMoment;
import app.bolo.learner.domain.Goal;
import app.bolo.learner.domain.Profile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProfileServiceTest {

    static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    final ProfilePort profiles = mock(ProfilePort.class);
    final ProfileService service = new ProfileService(profiles, new BoloClock(Clock.fixed(NOW, ZoneOffset.UTC)));
    final LearnerId learner = LearnerId.newId();

    @Test
    void refusesAnUnknownGoal() {
        assertThatThrownBy(() -> service.update(learner, "become_famous", "phone_calls", 5))
                .isInstanceOfSatisfying(RefusedException.class, e -> assertThat(e.code()).isEqualTo("goal"));
        verify(profiles, never()).upsert(any(), any(), any());
    }

    @Test
    void refusesAnUnknownFearedMoment() {
        assertThatThrownBy(() -> service.update(learner, "job_interviews", "dentist", 5))
                .isInstanceOfSatisfying(RefusedException.class, e -> assertThat(e.code()).isEqualTo("feared-moment"));
    }

    @Test
    void refusesTalkMinutesOtherThanFiveOrTen() {
        assertThatThrownBy(() -> service.update(learner, "job_interviews", "phone_calls", 7))
                .isInstanceOfSatisfying(RefusedException.class, e -> assertThat(e.code()).isEqualTo("talk-minutes"));
    }

    @Test
    void storesAValidProfileStampedWithNow() {
        service.update(learner, "job_interviews", "phone_calls", 10);
        verify(profiles).upsert(eq(learner),
                eq(new Profile(Goal.JOB_INTERVIEWS, FearedMoment.PHONE_CALLS, 10)), eq(NOW));
    }
}
