package app.bolo.talk.application.service;

import app.bolo.talk.domain.FreePlan;
import app.bolo.talk.application.port.in.FreeTalksUseCase.FreeTalks;
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
public class TalkSessionService implements StartTalkUseCase, app.bolo.talk.application.port.in.FreeTalksUseCase {

    /** A talk older than this no longer holds a live slot, so a crashed relay can't block talking forever. */
    static final Duration SLOT_HOLD = Duration.ofMinutes(30);

    private final TalkSessionPort sessions;
    private final CurrentLearnerUseCase learners;
    private final BoloClock clock;
    private final String policyVersion;
    private final int maxLive;
    private final Duration attachWithin;
    private final app.bolo.learner.application.port.in.VoiceAccessUseCase voiceAccess;
    private final app.bolo.learner.application.port.in.FreePlanKeyUseCase freePlanKeys;
    private final PlansService plans;
    @Value("${bolo.talk.starts-per-minute:10}")
    private int startsPerMinute = 10;


    public TalkSessionService(TalkSessionPort sessions, CurrentLearnerUseCase learners, BoloClock clock,
                              @Value("${bolo.policy-version}") String policyVersion,
                              @Value("${bolo.talk.max-live-sessions}") int maxLive,
                              @Value("${bolo.talk.attach-within-ms}") long attachWithinMs,
                              app.bolo.learner.application.port.in.VoiceAccessUseCase voiceAccess,
                              app.bolo.learner.application.port.in.FreePlanKeyUseCase freePlanKeys, PlansService plans) {
        this.plans = plans;
        this.voiceAccess = voiceAccess;
        this.freePlanKeys = freePlanKeys;
        this.sessions = sessions;
        this.learners = learners;
        this.clock = clock;
        this.policyVersion = policyVersion;
        this.maxLive = maxLive;
        this.attachWithin = Duration.ofMillis(attachWithinMs);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public TalkView start(LearnerId learner) {
        LearnerView view = consented(learner).orElseThrow(() -> new RefusedException("no-consent"));
        // Review (P1d): starts fired at once must not each pass the free-plan check; one at a time, in one transaction.
        sessions.lockStarts();
        // Review (P2a): a loop of starts can't run up the count or the bill.
        if (sessions.startsBy(learner, clock.now().minusSeconds(60)) >= startsPerMinute) throw new app.bolo.common.error.TooManyRequestsException();
        // P1d: a guest signs in before the first talk (only while sign-in is on); an account talks on the free plan.
        if (!voiceAccess.guestMayUseVoice(view)) throw new RefusedException("sign-in");
        if (!view.unlimited() && "account".equals(view.kind()) && allowanceOf(learner).left() == 0) {
            throw new RefusedException("free-limit");
        }
        // P2a: the global daily cap protects the bill; unlimited learners (probes, founder) never meet it.
        if (!view.unlimited() && plans.todayCount() >= plans.dailyCap()) throw new RefusedException("daily-cap");
        int chosen = view.profile() == null ? FreePlan.FREE_MINUTES : view.profile().talkMinutes();
        // A free-plan talk is 5 minutes (ACC-5). Guests only talk with sign-in off or when unlimited: no plan applies.
        boolean onFreePlan = !view.unlimited() && "account".equals(view.kind());
        int minutes = onFreePlan ? FreePlan.FREE_MINUTES : chosen;
        var now = clock.now();
        sessions.endOpenFor(learner, EndReason.DISCONNECTED, now);
        TalkSession session = new TalkSession(TalkSessionId.newId(), learner, minutes, now, null);
        if (!sessions.insertIfBelowCap(session, maxLive, now.minus(SLOT_HOLD), now.minus(attachWithin))) {
            throw new RefusedException("busy");
        }
        sessions.recordKeepChoice(session.id(), view.consent() != null && view.consent().keepRecordings());
        return toView(session, view);
    }

    @Override
    public Optional<FreeTalks> allowance(LearnerId learner) {
        LearnerView view = learners.view(learner);
        if (view.unlimited() || !"account".equals(view.kind())) return Optional.empty();
        var a = allowanceOf(learner);
        return Optional.of(new FreeTalks(a.window(), a.left(), a.total(), a.resetsOn(), a.plan()));
    }

    @Override
    public History history(LearnerId learner) {
        boolean returning = freePlanKeys.freePlanKey(learner).map(k -> !sessions.rememberedStarts(k).isEmpty()).orElse(false);
        return new History(sessions.countedStarts(learner).size(), sessions.finishedTalks(learner), returning);
    }

    private app.bolo.talk.domain.PlanAllowance.Allowance allowanceOf(LearnerId learner) {
        var starts = new java.util.ArrayList<>(sessions.countedStarts(learner));
        // P1d task 6: free talks used by an earlier, deleted account of the same Google user still count.
        freePlanKeys.freePlanKey(learner).ifPresent(key -> starts.addAll(sessions.rememberedStarts(key)));
        // P2a: an active paid plan (granted, earned or paid) gives its monthly talks.
        return plans.allowance(learner, starts);
    }

    /** Delete everything (ACC-4): the account's counted free talks are kept under its one-way key (P1d task 6). */
    @org.springframework.context.event.EventListener
    public void onLearnerDeleting(app.bolo.common.event.LearnerDeleting e) {
        if (e.freePlanKey() != null) sessions.rememberFreeTalks(e.learner(), e.freePlanKey());
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
        boolean slow = view.settings() != null && "slower".equals(view.settings().buddySpeed());
        var set = view.settings();
        String voice = set == null || set.voiceAccent() == null || set.voiceGender() == null ? null : set.voiceAccent() + "-" + set.voiceGender();
        return new TalkView(s.id(), s.minutes(), p == null ? null : p.goal(), p == null ? null : p.fearedMoment(), slow, voice,
                set == null ? null : set.mood());
    }

    /** A guest joined an account (US-8.1, US-8.4): its talks move with it, in the sign-in transaction. */
    @org.springframework.context.event.EventListener
    public void onLearnerMerged(app.bolo.common.event.LearnerMerged e) {
        sessions.moveLearner(e.from(), e.to());
    }
}
