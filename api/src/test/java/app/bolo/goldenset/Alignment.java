package app.bolo.goldenset;

import app.bolo.assessment.domain.SpokenWord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lines the vendor's words up with the reference text, so a teacher's label and Bolo's flag meet on the same word. */
final class Alignment {

    /** How far ahead a word may match its reference token (a dropped or merged word in between). */
    static final int WINDOW = 3;

    private Alignment() {
    }

    static String norm(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']", "");
    }

    static List<String> tokens(String reference) {
        List<String> out = new ArrayList<>();
        Arrays.stream(reference.trim().split("\\s+")).map(Alignment::norm).filter(t -> !t.isEmpty()).forEach(out::add);
        return out;
    }

    /** Input position → reference index. Insertions get none; an unmatched word gets none and doesn't move the cursor. */
    static Map<Integer, Integer> align(List<SpokenWord> words, List<String> tokens) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        int cursor = 0;
        for (int i = 0; i < words.size(); i++) {
            SpokenWord w = words.get(i);
            if ("Insertion".equals(w.errorType())) continue;
            String n = norm(w.word());
            for (int j = cursor; j < Math.min(tokens.size(), cursor + WINDOW + 1); j++) {
                if (tokens.get(j).equals(n)) {
                    out.put(i, j);
                    cursor = j + 1;
                    break;
                }
            }
        }
        return out;
    }
}
