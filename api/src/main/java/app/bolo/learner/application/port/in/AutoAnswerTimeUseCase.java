package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

/** AX-7 (2026-11-14): Bolo's automatic Time to answer, picked by the talk module after every talk. */
public interface AutoAnswerTimeUseCase {

    /** Saves Bolo's pick ("quick" | "normal" | "relaxed"); the learner's own choice is untouched. Refuses with "answer-time". */
    void record(LearnerId learner, String speed);
}
