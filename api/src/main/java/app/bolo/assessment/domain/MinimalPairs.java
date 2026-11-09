package app.bolo.assessment.domain;

import java.util.List;
import java.util.Map;

/**
 * Minimal pairs for each sound pattern (DRL-2): 3 listening pairs {sound word, other word} and 5 words to say. Real words
 * only; to be reviewed by the teacher with the practice sentences.
 */
public final class MinimalPairs {

    /** listen: {a, b, target}; target is the word with the pattern's sound. */
    public record Deck(List<String[]> listen, List<String> say) {
    }

    private static Deck deck(String[][] pairs, String... say) {
        return new Deck(java.util.Arrays.stream(pairs).map(p -> new String[]{p[0], p[1], p[0]}).toList(), List.of(say));
    }

    private static final Map<SoundPattern, Deck> DECKS = Map.of(
            SoundPattern.V_BH, deck(new String[][]{{"very", "berry"}, {"vest", "best"}, {"van", "ban"}}, "very", "voice", "video", "seven", "live"),
            SoundPattern.W_V, deck(new String[][]{{"wet", "vet"}, {"wine", "vine"}, {"west", "vest"}}, "we", "water", "work", "window", "away"),
            SoundPattern.TH_TD, deck(new String[][]{{"three", "tree"}, {"thin", "tin"}, {"path", "pat"}}, "think", "three", "thank", "bath", "mouth"),
            SoundPattern.Z_J, deck(new String[][]{{"zest", "jest"}, {"zig", "jig"}, {"buzz", "budge"}}, "zoo", "zero", "easy", "busy", "prize"),
            SoundPattern.S_SH, deck(new String[][]{{"ship", "sip"}, {"she", "see"}, {"shell", "sell"}}, "she", "shop", "fish", "wash", "sure"),
            SoundPattern.S_CLUSTER_VOWEL, deck(new String[][]{{"steam", "esteem"}, {"state", "estate"}, {"scape", "escape"}},
                    "school", "speak", "street", "study", "smile"));

    private MinimalPairs() {
    }

    public static Deck of(SoundPattern pattern) {
        return DECKS.get(pattern);
    }
}
