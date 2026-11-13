package app.bolo.assessment.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AX-8: for a learner who stammers, words that are part of the stammer are not judged for sounds: a word said again
 * straight after itself ("I I", "to to") and a broken-off start of the next word ("w- want", "wa want": 1 to 3
 * letters that begin the next, longer word). The turn text is unchanged.
 */
public final class StammerFilter {

    private StammerFilter() {
    }

    public static List<TurnScore> drop(List<TurnScore> turns) {
        return turns.stream().map(t -> new TurnScore(t.idx(), t.text(), keep(t.words()))).toList();
    }

    private static List<SpokenWord> keep(List<SpokenWord> words) {
        List<SpokenWord> kept = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (i + 1 < words.size() && partOfStammer(letters(words.get(i).word()), letters(words.get(i + 1).word()))) continue;
            kept.add(words.get(i));
        }
        return kept;
    }

    private static boolean partOfStammer(String w, String next) {
        if (w.isEmpty() || next.isEmpty()) return false;
        if (w.equals(next)) return true;
        return w.length() <= 3 && w.length() < next.length() && next.startsWith(w);
    }

    private static String letters(String w) {
        return w == null ? "" : w.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
