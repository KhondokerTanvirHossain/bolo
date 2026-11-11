package app.bolo.talk.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.TalkSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TalkSessionPort {

    /**
     * Inserts the talk unless {@code cap} talks still hold a slot; atomic. A live talk holds a slot if it attached a
     * socket and started after {@code liveSince}, or never attached and started after {@code unattachedSince}.
     */
    boolean insertIfBelowCap(TalkSession session, int cap, Instant liveSince, Instant unattachedSince);

    /** Marks the talk attached to a socket; false if it's not this learner's, ended, already attached or too old. */
    boolean attach(TalkSessionId id, LearnerId learner, Instant unattachedSince, Instant at);

    void endOpenFor(LearnerId learner, EndReason reason, Instant at);

    void endAllOpen(EndReason reason, Instant at);

    boolean anyAttachedLive(Instant liveSince);

    /** Ended talks whose audio is due for deletion (NFR-14), not yet purged. */
    List<TalkSessionId> audioDue(Instant noConsentEndedBefore, Instant anyEndedBefore);

    void markAudioPurged(TalkSessionId id, Instant at);

    Optional<TalkSession> findLive(TalkSessionId id, LearnerId learner);

    void end(TalkSessionId id, EndReason reason, Instant at);

    /** A guest joined an account (LearnerMerged): its rows become the account's. */
    void moveLearner(LearnerId from, LearnerId to);

    /**
     * When each of the learner's free-plan talks started. A talk counts once its socket opened if it is still open, its
     * feedback is scoring or ready, or it ran 90 s or more (Bolo ran, whatever the learner said). A failed debrief never
     * counts, nor a talk that dropped within 90 s without feedback.
     */
    java.util.List<java.time.Instant> countedStarts(LearnerId learner);

    /** P1d task 6: keeps the learner's counted free-talk starts under a one-way key before the account is deleted. */
    void rememberFreeTalks(LearnerId learner, String key);

    java.util.List<java.time.Instant> rememberedStarts(String key);

    void forgetFreeTalkUseBefore(java.time.Instant before);

    /** Serialises talk starts until the caller's transaction ends (the free-plan check, ending old talks and the insert). */
    void lockStarts();

    /** Talks that ended after at least 90 s, or with feedback scoring or ready (P2d: a friend "finished a talk"). */
    int finishedTalks(LearnerId learner);

    /** Talk starts (any) by this learner since {@code since}. */
    int startsBy(LearnerId learner, java.time.Instant since);

    /** The keep-recordings choice this talk is made under (US-8.5); the retention sweep follows it. */
    void recordKeepChoice(app.bolo.common.id.TalkSessionId talk, boolean keep);

    /** Which of these talks still exist (audio of the others is orphaned). */
    java.util.Set<app.bolo.common.id.TalkSessionId> existing(java.util.Collection<app.bolo.common.id.TalkSessionId> ids);

    java.util.List<app.bolo.common.id.TalkSessionId> idsFor(LearnerId learner);
}
