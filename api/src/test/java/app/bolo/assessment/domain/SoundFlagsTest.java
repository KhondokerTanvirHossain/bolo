package app.bolo.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SoundFlagsTest {

    static SpokenWord w(String word, double acc, String error, SpokenSound... sounds) {
        return new SpokenWord(word, 0, 100, acc, error, List.of(sounds));
    }

    static SpokenSound s(String symbol, double acc) {
        return new SpokenSound(symbol, acc);
    }

    @Test
    void aWeakPatternSoundInAnUnclearWordIsFlagged() {
        assertThat(SoundFlags.flags(List.of(w("very", 55, "None", s("v", 30), s("ɛ", 90))), 60, 75))
                .containsExactly(new SoundFlags.Flag(0, "very", SoundPattern.V_BH, 30));
    }

    @Test
    void aWeakSoundInAClearWordIsAccent() {
        assertThat(SoundFlags.flags(List.of(w("very", 80, "None", s("v", 30))), 60, 75)).isEmpty();
    }

    @Test
    void theExtraVowelIsFlaggedOnTheClusterWord() {
        assertThat(SoundFlags.flags(List.of(w("i", 40, "Insertion"), w("school", 90, "None", s("s", 90))), 60, 75))
                .containsExactly(new SoundFlags.Flag(1, "school", SoundPattern.S_CLUSTER_VOWEL, 40));
    }

    @Test
    void aRepeatedWordBeforeAClusterIsNotTheExtraVowel() {
        assertThat(SoundFlags.flags(List.of(w("the", 60, "Insertion"), w("the", 90, "None"), w("station", 90, "None")), 60, 75))
                .isEmpty();
    }

    @Test
    void theThresholdIsAParameter() {
        List<SpokenWord> words = List.of(w("very", 60, "None", s("v", 55)));
        assertThat(SoundFlags.flags(words, 60, 75)).hasSize(1);
        assertThat(SoundFlags.flags(words, 50, 75)).isEmpty();
    }

    @Test
    void patternsInNamesTheOpportunities() {
        assertThat(SoundFlags.patternsIn(w("think", 90, "None", s("θ", 90), s("ɪ", 90)))).containsExactly(SoundPattern.TH_TD);
        assertThat(SoundFlags.patternsIn(w("snow", 90, "None", s("s", 90), s("n", 90)))).containsExactly(SoundPattern.S_CLUSTER_VOWEL);
        assertThat(SoundFlags.patternsIn(w("cat", 90, "None", s("k", 90)))).isEmpty();
    }

    /** Practice tells a learner one sound per word; "shown" is that list, and the accuracy harness measures it. */
    @Test
    void shownKeepsTheFirstWeakSoundPerWord() {
        var words = List.of(w("i", 40, "Insertion"), w("these", 50, "None", s("ð", 20), s("z", 30)), w("school", 90, "None", s("s", 90)));
        assertThat(SoundFlags.shown(SoundFlags.flags(List.of(words.get(1)), 60, 75)))
                .extracting(SoundFlags.Flag::pattern).containsExactly(SoundPattern.TH_TD);
        assertThat(SoundFlags.shown(SoundFlags.flags(List.of(w("i", 40, "Insertion"), w("smooth", 50, "None", s("s", 90), s("ð", 20))), 60, 75)))
                .extracting(SoundFlags.Flag::pattern).containsExactly(SoundPattern.S_CLUSTER_VOWEL, SoundPattern.TH_TD);
    }
}
