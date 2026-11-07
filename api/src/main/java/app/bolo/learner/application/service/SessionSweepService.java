package app.bolo.learner.application.service;

import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.SweepSessionsUseCase;
import app.bolo.learner.application.port.out.LearnerPort;
import app.bolo.learner.application.port.out.SessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionSweepService implements SweepSessionsUseCase {

    /** A guest who never got past the consent card within a day is gone; nothing of theirs is kept. */
    static final Duration ABANDONED_GUEST = Duration.ofDays(1);

    private final SessionPort sessions;
    private final LearnerPort learners;
    private final BoloClock clock;

    @Override
    @Transactional
    public void sweep() {
        sessions.deleteIdleSince(clock.now().minus(GuestService.IDLE_EXPIRY));
        learners.deleteGuestsWithoutConsentCreatedBefore(clock.now().minus(ABANDONED_GUEST));
    }
}
