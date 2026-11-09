package app.bolo.coaching.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.util.Optional;

public interface DebriefUseCase {

    /** The debrief JSON ({"status": scoring | ready | too_short | failed, ...}) for the talk's own learner only. */
    Optional<String> view(LearnerId learner, TalkSessionId talk);

    /**
     * DBF-6: {topic, turns:[{speaker: "bolo"|"learner", text}], marks:[{turn, start, end, fix, title}]} — turn indexes
     * {@code turns}, start/end are character offsets in its text, fix is the card's 1-based number. Empty unless the
     * learner owns the talk and its feedback is ready.
     */
    Optional<String> chat(LearnerId learner, TalkSessionId talk);

    /** DBF-4: removes the fix card (by its stable id) and logs it; false if there is no such card for this learner. */
    boolean dispute(LearnerId learner, TalkSessionId talk, String fixId);

    /** The learner has seen this ready debrief (Home stops offering "Finish your feedback"); false if not theirs or not ready. */
    boolean markViewed(LearnerId learner, TalkSessionId talk);

    /**
     * US-8.6: deletes this talk's audio (clips and model lines) at once; the feedback text stays and its audio links
     * become null. False when it isn't the learner's talk.
     */
    boolean deleteRecording(LearnerId learner, TalkSessionId talk);

    /** A clip or model file of the learner's own debrief. */
    Optional<byte[]> media(LearnerId learner, TalkSessionId talk, String file);
}
