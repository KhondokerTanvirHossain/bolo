package app.bolo.assessment.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores a scripted attempt (US-3.2, US-4.3), judging clarity, not accent. A word is in focus when a pattern sound
 * scores under 60 inside a word under 75, when it was left out, or when an extra vowel came before its s + consonant
 * start ("ischool"). Nailed: nothing in focus and words average 70+. Closer: at most one, averaging 70+.
 */
public final class AttemptScorer {

    static final double SOUND_BELOW = SoundFlags.PRACTICE_SOUND_BELOW;
    static final double WORD_BELOW = SoundFlags.WORD_BELOW;
    static final String MISSING_TIP = "Say every word, even the small ones.";

    private AttemptScorer() {
    }

    public static AttemptResult score(List<SpokenWord> words) {
        return score(words, false);
    }

    /**
     * {@code upgrade}: Try it on a sentence upgrade is judged on clarity and flow, not word for word (US-4.4), so a
     * dropped word isn't marked.
     */
    public static AttemptResult score(List<SpokenWord> words, boolean upgrade) {
        // Kept in sentence order: each entry remembers the word position it belongs to.
        List<Map.Entry<Integer, AttemptResult.Focus>> at = new ArrayList<>();
        Set<String> clusterWords = new HashSet<>();
        for (SoundFlags.Flag f : SoundFlags.shown(SoundFlags.flags(words, SOUND_BELOW, WORD_BELOW))) {
            if (f.pattern() == SoundPattern.S_CLUSTER_VOWEL) {
                if (clusterWords.add(f.word())) {
                    at.add(Map.entry(f.position(), new AttemptResult.Focus(f.word(), f.pattern(), AttemptResult.Kind.EXTRA_VOWEL, f.pattern().tip())));
                }
            } else {
                at.add(Map.entry(f.position(), new AttemptResult.Focus(f.word(), f.pattern(), AttemptResult.Kind.SOUND, f.pattern().tip())));
            }
        }
        double sum = 0;
        int counted = 0;
        for (int i = 0; i < words.size(); i++) {
            SpokenWord w = words.get(i);
            switch (w.errorType() == null ? "None" : w.errorType()) {
                case "Insertion" -> { }
                case "Omission" -> {
                    if (!upgrade) at.add(Map.entry(i, new AttemptResult.Focus(w.word(), null, AttemptResult.Kind.MISSING, MISSING_TIP)));
                }
                default -> {
                    sum += w.accuracy();
                    counted++;
                }
            }
        }
        at.sort(Map.Entry.comparingByKey());
        List<AttemptResult.Focus> focus = at.stream().map(Map.Entry::getValue).toList();
        if (counted == 0) return new AttemptResult(AttemptResult.Level.ONCE_MORE, List.copyOf(focus));
        double mean = sum / counted;
        // Nothing to work on and clear enough is "Nailed it" — a higher bar on the overall score would grade the accent.
        AttemptResult.Level level = focus.isEmpty() && mean >= 70 ? AttemptResult.Level.NAILED
                : focus.size() <= 1 && mean >= 70 ? AttemptResult.Level.CLOSER : AttemptResult.Level.ONCE_MORE;
        return new AttemptResult(level, List.copyOf(focus));
    }
}
