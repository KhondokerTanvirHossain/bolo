package app.bolo.assessment.domain;

/** DBF-5 levels. Sentence building and vocabulary come from the Coach's reading of the transcript. */
public final class SkillLevels {

    private SkillLevels() {
    }

    public static Level pronunciation(double meanWordAccuracy) {
        if (meanWordAccuracy >= 85) return Level.STRONG;
        if (meanWordAccuracy >= 70) return Level.GOOD;
        return Level.BUILDING;
    }

    public static Level fluency(int wordsPerMinute, double longPausesPerMinute) {
        if (wordsPerMinute >= 110 && longPausesPerMinute <= 2) return Level.STRONG;
        if (wordsPerMinute >= 80) return Level.GOOD;
        return Level.BUILDING;
    }
}
