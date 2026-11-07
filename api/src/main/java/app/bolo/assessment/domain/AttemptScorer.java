package app.bolo.assessment.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores a scripted attempt (US-3.2, US-4.3), judging clarity, not accent. A word is in focus when a pattern sound
 * scores under 60 inside a word under 75, when it was left out, or when an extra vowel came before its s + consonant
 * start ("ischool"). Nailed: nothing in focus and words average 85+. Closer: at most one, averaging 70+.
 */
public final class AttemptScorer {

    static final double SOUND_BELOW = 60;
    static final double WORD_BELOW = 75;
    static final String MISSING_TIP = "Say every word, even the small ones.";

    private AttemptScorer() {
    }

    public static AttemptResult score(List<SpokenWord> words) {
        List<AttemptResult.Focus> focus = new ArrayList<>();
        double sum = 0;
        int counted = 0;
        for (int i = 0; i < words.size(); i++) {
            SpokenWord w = words.get(i);
            switch (w.errorType() == null ? "None" : w.errorType()) {
                case "Insertion" -> {
                    SpokenWord next = nextSpoken(words, i);
                    if (next != null && startsWithSCluster(next.word()) && focus.stream().noneMatch(f -> f.word().equals(next.word()))) {
                        focus.add(new AttemptResult.Focus(next.word(), SoundPattern.S_CLUSTER_VOWEL, AttemptResult.Kind.EXTRA_VOWEL,
                                SoundPattern.S_CLUSTER_VOWEL.tip()));
                    }
                }
                case "Omission" -> focus.add(new AttemptResult.Focus(w.word(), null, AttemptResult.Kind.MISSING, MISSING_TIP));
                default -> {
                    sum += w.accuracy();
                    counted++;
                    if (w.accuracy() < WORD_BELOW) {
                        w.phonemes().stream().filter(p -> p.accuracy() < SOUND_BELOW)
                                .map(p -> SoundPattern.of(p.symbol())).flatMap(java.util.Optional::stream).findFirst()
                                .ifPresent(p -> focus.add(new AttemptResult.Focus(w.word(), p, AttemptResult.Kind.SOUND, p.tip())));
                    }
                }
            }
        }
        if (counted == 0) return new AttemptResult(AttemptResult.Level.ONCE_MORE, List.copyOf(focus));
        double mean = sum / counted;
        AttemptResult.Level level = focus.isEmpty() && mean >= 85 ? AttemptResult.Level.NAILED
                : focus.size() <= 1 && mean >= 70 ? AttemptResult.Level.CLOSER : AttemptResult.Level.ONCE_MORE;
        return new AttemptResult(level, List.copyOf(focus));
    }

    private static SpokenWord nextSpoken(List<SpokenWord> words, int i) {
        for (int k = i + 1; k < words.size(); k++) {
            if (!"Insertion".equals(words.get(k).errorType())) return words.get(k);
        }
        return null;
    }

    static boolean startsWithSCluster(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return w.length() > 1 && w.charAt(0) == 's' && "ckmnptlw".indexOf(w.charAt(1)) >= 0;
    }
}
