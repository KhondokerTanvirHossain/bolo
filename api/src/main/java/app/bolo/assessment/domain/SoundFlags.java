package app.bolo.assessment.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one sound-flag rule (US-3.2: clarity, not accent), shared by practice, "Try it" and the Gate 1 accuracy
 * harness, so the harness measures exactly what learners get. A word is flagged for a pattern when one of the
 * pattern's sounds scores under {@code soundBelow} inside a word scoring under {@code wordBelow}; the s-cluster
 * extra vowel is flagged on the s + consonant word that follows a lone inserted vowel ("i school").
 */
public final class SoundFlags {

    public static final double PRACTICE_SOUND_BELOW = 60;
    public static final double WORD_BELOW = 75;

    /** {@code position} is the index in the input word list; {@code soundScore} the weakest pattern sound (or the inserted vowel's score). */
    public record Flag(int position, String word, SoundPattern pattern, double soundScore) {
    }

    private SoundFlags() {
    }

    public static List<Flag> flags(List<SpokenWord> words, double soundBelow, double wordBelow) {
        List<Flag> out = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            SpokenWord w = words.get(i);
            String error = w.errorType() == null ? "None" : w.errorType();
            if (error.equals("Insertion")) {
                int next = nextSpoken(words, i);
                if (next >= 0 && isLoneShortVowel(w.word()) && startsWithSCluster(words.get(next).word())) {
                    int at = next;
                    if (out.stream().noneMatch(f -> f.position() == at && f.pattern() == SoundPattern.S_CLUSTER_VOWEL)) {
                        out.add(new Flag(at, words.get(at).word(), SoundPattern.S_CLUSTER_VOWEL, w.accuracy()));
                    }
                }
            } else if (!error.equals("Omission") && w.accuracy() < wordBelow) {
                Map<SoundPattern, Double> weakest = new LinkedHashMap<>();
                for (SpokenSound s : w.phonemes()) {
                    if (s.accuracy() >= soundBelow) continue;
                    SoundPattern.of(s.symbol()).ifPresent(p -> weakest.merge(p, s.accuracy(), Math::min));
                }
                int at = i;
                weakest.forEach((p, score) -> out.add(new Flag(at, w.word(), p, score)));
            }
        }
        out.sort((a, b) -> Integer.compare(a.position(), b.position()));
        return out;
    }

    /** The patterns this word gives a chance to get wrong: its pattern sounds, and an s + consonant start. */
    public static List<SoundPattern> patternsIn(SpokenWord w) {
        List<SoundPattern> out = new ArrayList<>();
        for (SpokenSound s : w.phonemes()) SoundPattern.of(s.symbol()).filter(p -> !out.contains(p)).ifPresent(out::add);
        if (startsWithSCluster(w.word())) out.add(SoundPattern.S_CLUSTER_VOWEL);
        return out;
    }

    private static int nextSpoken(List<SpokenWord> words, int i) {
        for (int k = i + 1; k < words.size(); k++) {
            if (!"Insertion".equals(words.get(k).errorType())) return k;
        }
        return -1;
    }

    static boolean isLoneShortVowel(String word) {
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return !w.isEmpty() && w.length() <= 2 && w.chars().allMatch(c -> "aeiouh".indexOf(c) >= 0) && "aeiou".indexOf(w.charAt(0)) >= 0;
    }

    public static boolean startsWithSCluster(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return w.length() > 1 && w.charAt(0) == 's' && "ckmnptlw".indexOf(w.charAt(1)) >= 0;
    }
}
