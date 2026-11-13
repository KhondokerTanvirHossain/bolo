package app.bolo.coaching.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.time.Instant;
import java.util.Optional;

public interface DebriefPort {

    /** audioPurged: the retention sweep removed the talk's audio; keptAudio: the keep choice it was recorded under. */
    record Stored(LearnerId learner, String status, String body, boolean audioPurged, boolean keptAudio) {
    }

    void start(TalkSessionId talk, Instant at);

    void delete(TalkSessionId talk);

    void save(TalkSessionId talk, String status, String body, int attempts, Instant at);

    Optional<Stored> find(TalkSessionId talk);

    /** The learner's most recent talk with a debrief. */
    record Last(String talkId, String status, boolean viewed, String body) {
    }

    Optional<Last> latestFor(LearnerId learner);

    /** viewed_at of the learner's ready debriefs (completed loops). */
    java.util.List<java.time.Instant> viewedReady(LearnerId learner);

    /** The body of the learner's latest ready debrief. */
    Optional<String> latestReadyBody(LearnerId learner);

    record Past(String talkId, java.time.Instant startedAt, int minutes, String status, String body) {
    }

    /** Every talk of the learner that has a debrief, oldest first. */
    java.util.List<Past> history(LearnerId learner);

    /** Sets viewed_at once, for the owner's ready debrief; true when it is (now or already) viewed. */
    boolean markViewed(LearnerId learner, TalkSessionId talk, Instant at);

    /** Ready debriefs this learner already had, other than this talk's. */
    int readyCountFor(LearnerId learner, TalkSessionId except);

    /** DBF-8: the learner's ready debriefs of talks that started before this one. */
    int readyCountBefore(LearnerId learner, TalkSessionId talk);

    void logDispute(TalkSessionId talk, int fixIndex, String kind, Instant at);

    /**
     * Read-modify-write of a ready debrief's body under a row lock: {@code change} returns the new body, or null to
     * leave it as is. Returns what {@code change} returned.
     */
    String updateReadyBody(TalkSessionId talk, java.util.function.UnaryOperator<String> change, Instant at);

    /** After a restart nothing is scoring any more. */
    void failAllScoring(Instant at);
}
