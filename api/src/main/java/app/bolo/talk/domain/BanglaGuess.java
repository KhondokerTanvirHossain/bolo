package app.bolo.talk.domain;

/**
 * Founder 2026-11-18: "it should understand Bangla". Spike through the live SDK (with pronunciation scoring): the English
 * recogniser is 0.91-0.95 sure on English and 0.47-0.81 on Bangla or mixed Bangla-English, where it writes nonsense
 * English. So a turn under {@code maxEnglishConfidence} (or with no words) is checked by Azure's language
 * identification, which decides: the check only answers when it heard Bangla.
 */
public record BanglaGuess(double maxEnglishConfidence, double minBanglaConfidence) {

    /**
     * An English turn worth checking: no words at all, words the recogniser was unsure of, or no score yet (a Done tap
     * before the recogniser's final segment, review 2026-11-18); the language check decides.
     */
    public boolean worthChecking(String englishText, Double englishConfidence) {
        if (englishText == null || englishText.isBlank()) return true;
        return englishConfidence == null || englishConfidence < maxEnglishConfidence;
    }

    /** Language identification heard Bangla: its text is Bangla script and it is sure enough. */
    public boolean accept(String banglaText, double banglaConfidence, Double englishConfidence) {
        return isBangla(banglaText) && banglaConfidence >= minBanglaConfidence;
    }

    /** At least half the words carry Bangla letters (U+0980-U+09FF). */
    public static boolean isBangla(String text) {
        return app.bolo.common.text.Bangla.is(text);
    }
}
