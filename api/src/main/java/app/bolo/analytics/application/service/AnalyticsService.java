package app.bolo.analytics.application.service;

import app.bolo.analytics.application.port.in.RecordEventUseCase;
import app.bolo.analytics.application.port.out.EventPort;
import app.bolo.analytics.domain.EventRules;
import app.bolo.common.error.RefusedException;
import app.bolo.common.error.TooManyRequestsException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.talk.application.port.in.TalkEnded;
import app.bolo.talk.application.port.in.TalkStarted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stores §10 events: allow-listed browser events (rate-limited) and the talk's own start and end. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService implements RecordEventUseCase {

    static final int PER_MINUTE = 60;
    private static final int PRUNE_ABOVE = 10_000;

    private final EventPort events;
    private final BoloClock clock;
    /** learner → {window start (epoch minute), count}. */
    private final Map<LearnerId, long[]> windows = new ConcurrentHashMap<>();

    @Override
    public void record(LearnerId learner, String name, Map<String, Object> props) {
        if (EventRules.problem(name, props).isPresent()) throw new RefusedException("event");
        Instant now = clock.now();
        long minute = now.getEpochSecond() / 60;
        if (windows.size() > PRUNE_ABOVE) windows.values().removeIf(w -> w[0] < minute);
        long[] w = windows.compute(learner, (k, v) -> v == null || v[0] != minute ? new long[]{minute, 1} : new long[]{minute, v[1] + 1});
        if (w[1] > PER_MINUTE) throw new TooManyRequestsException();
        events.insert(learner, name, props == null ? Map.of() : props, now);
    }

    /** Runs inside the talk's own path; analytics must never break a talk (or the Coach's clean-up listening after it). */
    @EventListener
    public void onTalkStarted(TalkStarted e) {
        quietly(() -> events.insert(e.learner(), "session_started", Map.of(), clock.now()));
    }

    @EventListener
    public void onTalkEnded(TalkEnded e) {
        quietly(() -> events.insert(e.learner(), "session_ended", Map.of("turns", e.learnerTurns(), "end_reason", e.reason()), clock.now()));
    }

    @EventListener
    public void onReminderSent(app.bolo.reminder.application.port.in.ReminderSent e) {
        quietly(() -> events.insert(e.learner(), "reminder_sent", Map.of("slot", e.slot(), "hook_type", e.hookType()), clock.now()));
    }

    @EventListener
    public void onReminderOpened(app.bolo.reminder.application.port.in.ReminderOpened e) {
        quietly(() -> events.insert(e.learner(), "reminder_opened", Map.of(), clock.now()));
    }

    private static void quietly(Runnable insert) {
        try {
            insert.run();
        } catch (RuntimeException ex) {
            log.warn("analytics event not stored: {}", ex.getClass().getSimpleName());
        }
    }

    /** A guest joined an account (US-8.1, US-8.4): its events move with it, in the sign-in transaction. */
    @org.springframework.context.event.EventListener
    public void onLearnerMerged(app.bolo.common.event.LearnerMerged e) {
        events.moveLearner(e.from(), e.to());
    }
}
