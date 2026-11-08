package app.bolo.progress.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.Map;

public interface ProgressUseCase {

    /** Effort, the skill trends, my patterns and the latest 3 talks (ScreenProgress). */
    Map<String, Object> progress(LearnerId learner);

    /** Every past talk, newest first (ScreenHistory). */
    Map<String, Object> history(LearnerId learner);
}
