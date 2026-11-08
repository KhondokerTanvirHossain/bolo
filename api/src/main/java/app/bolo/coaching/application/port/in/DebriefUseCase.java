package app.bolo.coaching.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.util.Optional;

public interface DebriefUseCase {

    /** The debrief JSON ({"status": scoring | ready | too_short | failed, ...}) for the talk's own learner only. */
    Optional<String> view(LearnerId learner, TalkSessionId talk);

    /** DBF-4: removes the fix card (by its stable id) and logs it; false if there is no such card for this learner. */
    boolean dispute(LearnerId learner, TalkSessionId talk, String fixId);

    /** The learner has seen this ready debrief (Home stops offering "Finish your feedback"); false if not theirs or not ready. */
    boolean markViewed(LearnerId learner, TalkSessionId talk);

    /** A clip or model file of the learner's own debrief. */
    Optional<byte[]> media(LearnerId learner, TalkSessionId talk, String file);
}
