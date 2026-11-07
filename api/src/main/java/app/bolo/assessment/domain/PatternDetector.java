package app.bolo.assessment.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds sound patterns worth a fix (ASM-2, ASM-5). A word counts when one of a pattern's sounds scores under
 * {@link #SOUND_BELOW} inside a word scoring under {@link #WORD_BELOW}: a soft sound in a word that was still clear
 * is accent, not a clarity problem (US-3.2). A pattern needs {@link #MIN_COUNT} such words in the session.
 */
public final class PatternDetector {

    static final double SOUND_BELOW = 50;
    static final double WORD_BELOW = 75;
    static final int MIN_COUNT = 2;

    private PatternDetector() {
    }

    public static List<SoundIssue> candidates(List<TurnScore> turns) {
        Map<SoundPattern, List<WordAt>> hits = new LinkedHashMap<>();
        for (TurnScore t : turns) {
            for (SpokenWord w : t.words()) {
                if (w.accuracy() >= WORD_BELOW) continue;
                Map<SoundPattern, Double> worstInWord = new LinkedHashMap<>();
                for (SpokenSound s : w.phonemes()) {
                    if (s.accuracy() >= SOUND_BELOW) continue;
                    SoundPattern.of(s.symbol()).ifPresent(p -> worstInWord.merge(p, s.accuracy(), Math::min));
                }
                worstInWord.forEach((p, score) -> hits.computeIfAbsent(p, k -> new ArrayList<>())
                        .add(new WordAt(t.idx(), w.word(), w.startMs(), w.endMs(), score)));
            }
        }
        return hits.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_COUNT)
                .map(e -> new SoundIssue(e.getKey(), e.getValue().stream()
                        .sorted(Comparator.comparingDouble(WordAt::soundScore)).toList()))
                .sorted(Comparator.comparingInt(SoundIssue::count).reversed())
                .toList();
    }
}
