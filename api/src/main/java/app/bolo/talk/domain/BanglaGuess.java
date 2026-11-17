package app.bolo.talk.domain;

/**
 * Founder 2026-11-18: "it should understand Bangla". Spike (Azure, 2026-11-18): the English recogniser is 0.82-0.90 sure
 * on English speech and under 0.25 on Bangla or mixed Bangla-English, where it writes romanised Bangla. The Bangla
 * recogniser writes Bangla in Bangla script (0.67-0.87) but is also 0.63-0.77 "sure" on English, so only the English
 * confidence decides whether to ask, and the Bangla answer must be clearly surer than the English one.
 */
public record BanglaGuess(double maxEnglishConfidence, double minBanglaConfidence) {

    /** How much surer the Bangla recognition must be than the English one. */
    static final double MARGIN = 0.25;

    /** An English turn worth hearing again in Bangla: no words at all, or words the recogniser was unsure of. */
    public boolean worthChecking(String englishText, Double englishConfidence) {
        if (englishText == null || englishText.isBlank()) return true;
        return englishConfidence != null && englishConfidence < maxEnglishConfidence;
    }

    /** The Bangla recognition is Bangla script, sure enough, and clearly surer than the English. */
    public boolean accept(String banglaText, double banglaConfidence, Double englishConfidence) {
        double english = englishConfidence == null ? 0 : englishConfidence;
        return isBangla(banglaText) && banglaConfidence >= minBanglaConfidence && banglaConfidence >= english + MARGIN;
    }

    /** At least half the words carry Bangla letters (U+0980-U+09FF). */
    public static boolean isBangla(String text) {
        return app.bolo.common.text.Bangla.is(text);
    }
}
