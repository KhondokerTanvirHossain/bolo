package app.bolo.assessment.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The Bangla-speaker sound patterns the Coach can name (ASM-2), by the IPA sounds Azure scores. Named the way
 * people say them (design-system copy): "The v sound · v as in van". The s-cluster extra vowel isn't detectable
 * without a script, so it waits for the "Try it" practice (plan B).
 */
public enum SoundPattern {
    V_BH("The v sound", "v as in van", "Top teeth touch your lower lip for v.", "v"),
    W_V("The w sound", "w as in we", "Round your lips, no teeth, for w.", "w"),
    TH_TD("The th sound", "th as in think", "Put the tip of your tongue between your teeth for th.", "θ", "ð"),
    Z_J("The z sound", "z as in zoo", "Keep your tongue behind your teeth and buzz for z.", "z"),
    S_SH("The sh sound", "sh as in ship", "Round your lips and pull your tongue back a little for sh.", "ʃ");

    private final String title;
    private final String example;
    private final String tip;
    private final Set<String> symbols;

    SoundPattern(String title, String example, String tip, String... symbols) {
        this.title = title;
        this.example = example;
        this.tip = tip;
        this.symbols = Set.of(symbols);
    }

    public String title() {
        return title;
    }

    public String example() {
        return example;
    }

    public String tip() {
        return tip;
    }

    public static Optional<SoundPattern> of(String symbol) {
        return Arrays.stream(values()).filter(p -> p.symbols.contains(symbol)).findFirst();
    }
}
