package app.bolo.assessment.domain;

import java.util.List;

/** One "Try it" attempt (US-4.3): a result word, and the words to work on. */
public record AttemptResult(Level level, List<Focus> focus) {

    public enum Level { NAILED, CLOSER, ONCE_MORE }

    public enum Kind { SOUND, MISSING, EXTRA_VOWEL }

    /** pattern is null for a missing word. */
    public record Focus(String word, SoundPattern pattern, Kind kind, String tip) {
    }
}
