package app.bolo.assessment.application.port.in;

import app.bolo.speech.application.port.in.ScoredWord;

import java.util.List;
import java.util.Optional;

/** Scores a finished talk's learner turns (ASM-1..6) and picks the debrief's fix slots (ASM-4). */
public interface AssessTalkUseCase {

    record TurnInput(int idx, String text, List<ScoredWord> words) {
    }

    /** pattern: the SoundPattern key, e.g. "V_BH" (practice opens on it). */
    record SoundCandidate(String pattern, String title, String example, String tip, int count, int turnIdx, String word, long startMs, long endMs) {
    }

    record HabitView(String kind, String word, int count, double averageWords) {
    }

    /** pronunciationLevel is null when no words were scored. Levels: BUILDING, GOOD, STRONG. */
    record TalkAssessment(long speechMs, List<SoundCandidate> sounds, Optional<HabitView> habit, String pronunciationLevel,
                          String fluencyLevel) {
    }

    /** kind: sound | upgrade | habit; index points into sounds or the validated upgrades. */
    record FixSlot(String kind, int index) {
    }

    default TalkAssessment assess(List<TurnInput> turns) {
        return assess(turns, false);
    }

    /** AX-8: with {@code stammer}, repeats and broken-off words aren't judged for sounds and blocks don't lower fluency. */
    TalkAssessment assess(List<TurnInput> turns, boolean stammer);

    List<FixSlot> plan(TalkAssessment assessment, int upgrades);
}
