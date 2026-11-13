package app.bolo.memory.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.time.LocalDate;
import java.util.List;

/** What Bolo remembers (spec 2026-11-13 "Bolo knows you"), as other modules and the web see it. */
public interface MemoryUseCase {

    /**
     * The brief for a talk that's opening: the note, the one follow-up to open with (it then rests), recent topics and 3
     * fresh ideas. Asked twice for the same talk, it returns the same follow-up and counts the talk once.
     */
    TalkBrief brief(LearnerId learner, TalkSessionId talk, String goal);

    /** Me → What Bolo remembers (ACC-9). Recent topics are never shown. {@code kind}: plan, worry or prep. */
    record MemoryView(boolean enabled, List<FollowUp> followUps, List<Item> about, List<Item> interests) {
    }

    record FollowUp(String id, String kind, String text, LocalDate said) {
    }

    record Item(String id, String text) {
    }

    MemoryView view(LearnerId learner);

    /** Removes one line (idempotent). It can come back only if the learner raises it again in a later talk. */
    void remove(LearnerId learner, String lineId);

    /** Off deletes the note and stops new notes; on starts from an empty note. */
    void setEnabled(LearnerId learner, boolean on);

    /** "Forget everything Bolo knows": empties the note; remembering stays as it was. */
    void forget(LearnerId learner);
}
