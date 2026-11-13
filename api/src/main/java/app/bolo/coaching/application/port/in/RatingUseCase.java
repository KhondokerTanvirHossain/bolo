package app.bolo.coaching.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.util.Optional;

/** DBF-8: "How was today?" — five faces and an optional typed note, after the feedback of some talks. */
public interface RatingUseCase {

    /** Whether to ask after this talk's feedback; empty when it isn't the learner's talk with feedback. */
    Optional<Boolean> ask(LearnerId learner, TalkSessionId talk);

    /**
     * Saves a score (1–5, or null for "Not now") and an optional note (≤ 300 characters, only with a score). A null
     * score never clears one already saved. False when it isn't the learner's talk. Refuses with "score" or "note".
     */
    boolean save(LearnerId learner, TalkSessionId talk, Integer score, String note);
}
