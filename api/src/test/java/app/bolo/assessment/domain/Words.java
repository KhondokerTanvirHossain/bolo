package app.bolo.assessment.domain;

import java.util.Arrays;
import java.util.List;

/** Test helpers for building scored words. */
final class Words {

    private Words() {
    }

    static SpokenWord word(String text, long start, long end, double accuracy, SpokenSound... sounds) {
        return new SpokenWord(text, start, end, accuracy, "None", Arrays.asList(sounds));
    }

    static SpokenSound sound(String symbol, double accuracy) {
        return new SpokenSound(symbol, accuracy);
    }

    static TurnScore turn(int idx, String text, SpokenWord... words) {
        return new TurnScore(idx, text, List.of(words));
    }
}
