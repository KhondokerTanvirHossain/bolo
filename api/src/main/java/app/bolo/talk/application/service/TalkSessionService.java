package app.bolo.talk.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.learner.application.port.in.LearnerView;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkView;
import app.bolo.talk.application.port.out.TalkSessionPort;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.TalkSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class TalkSessionService implements StartTalkUseCase {

    /** A talk older than this no longer holds a live slot, so a crashed relay can't block talking forever. */
    static final Duration SLOT_HOLD = Duration.ofMinutes(30);

    private final TalkSessionPort sessions;
    private final CurrentLearnerUseCase learners;
    private final BoloClock clock;
    private final String policyVersion;
    private final int maxLive;
    private final Duration attachWithin;

    public TalkSessionService(TalkSessionPort sessions, CurrentLearnerUseCase learners, BoloClock clock,
                              @Value("${bolo.policy-version}") String policyVersion,
                              @Value("${bolo.talk.max-live-sessions}") int maxLive,
                              @Value("${bolo.talk.attach-within-ms}") long attachWithinMs) {
        this.sessions = sessions;
        this.learners = learners;
        this.clock = clock;
        this.policyVersion = policyVersion;
        this.maxLive = maxLive;
        this.attachWithin = Duration.ofMillis(attachWithinMs);
    }

    @Override
    public TalkView start(LearnerId learner) {
        LearnerView view = consented(learner).orElseThrow(() -> new RefusedException("no-consent"));
        int minutes = view.profile() == null ? 5 : view.profile().talkMinutes();
        var now = clock.now();
        sessions.endOpenFor(learner, EndReason.DISCONNECTED, now);
        TalkSession session = new TalkSession(TalkSessionId.newId(), learner, minutes, now, null);
        if (!sessions.insertIfBelowCap(session, maxLive, now.minus(SLOT_HOLD), now.minus(attachWithin))) {
            throw new RefusedException("busy");
        }
        return toView(session, view);
    }

    @Override
    public Optional<TalkView> openFor(LearnerId learner, TalkSessionId id) {
        return consented(learner).flatMap(view -> sessions.findLive(id, learner).map(s -> toView(s, view)));
    }

    @Override
    public void end(TalkSessionId id, EndReason reason) {
        sessions.end(id, reason, clock.now());
    }

    @Override
    public Optional<TalkView> attach(LearnerId learner, TalkSessionId id) {
        var now = clock.now();
        return openFor(learner, id).filter(v -> sessions.attach(id, learner, now.minus(attachWithin), now));
    }

    @Override
    public boolean anyLive() {
        return sessions.anyAttachedLive(clock.now().minus(SLOT_HOLD));
    }

    @Override
    public void endAllOpen() {
        sessions.endAllOpen(EndReason.DISCONNECTED, clock.now());
    }

    private Optional<LearnerView> consented(LearnerId learner) {
        LearnerView view = learners.view(learner);
        boolean ok = view.consent() != null && policyVersion.equals(view.consent().policyVersion());
        return ok ? Optional.of(view) : Optional.empty();
    }

    private static TalkView toView(TalkSession s, LearnerView view) {
        var p = view.profile();
        return new TalkView(s.id(), s.minutes(), p == null ? null : p.goal(), p == null ? null : p.fearedMoment());
    }
}
