package app.bolo.memory.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** What Bolo remembers (spec 2026-11-13 "Bolo knows you"), as other modules and the web see it. */
public interface MemoryUseCase {

    /**
     * The brief for a talk that's opening: the note, the one follow-up to open with (it then rests), recent topics and 3
     * fresh ideas. Asked twice for the same talk, it returns the same follow-up and counts the talk once.
     */
    TalkBrief brief(LearnerId learner, TalkSessionId talk, String goal);
}
