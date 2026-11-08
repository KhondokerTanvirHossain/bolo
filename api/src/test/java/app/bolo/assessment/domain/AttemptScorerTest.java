package app.bolo.assessment.domain;

import app.bolo.speech.adapter.out.azure.AzureScoreParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static app.bolo.assessment.domain.Words.sound;
import static app.bolo.assessment.domain.Words.word;
import static org.assertj.core.api.Assertions.assertThat;

class AttemptScorerTest {

    static List<SpokenWord> fixture(String name) throws IOException {
        try (InputStream in = AttemptScorerTest.class.getResourceAsStream("/audio/" + name)) {
            return AzureScoreParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).orElseThrow().words().stream()
                    .map(w -> new SpokenWord(w.word(), w.startMs(), w.endMs(), w.accuracy(), w.errorType(),
                            w.phonemes().stream().map(p -> new SpokenSound(p.symbol(), p.accuracy())).toList()))
                    .toList();
        }
    }

    @Test
    void theExtraVowelBeforeSchoolAndStationIsFound() throws IOException {
        AttemptResult r = AttemptScorer.score(fixture("pa-scripted-ischool.json"));
        assertThat(r.focus()).extracting(AttemptResult.Focus::word).containsExactly("school", "station");
        assertThat(r.focus()).allMatch(f -> f.kind() == AttemptResult.Kind.EXTRA_VOWEL && f.pattern() == SoundPattern.S_CLUSTER_VOWEL);
        assertThat(r.level()).isNotEqualTo(AttemptResult.Level.NAILED);
    }

    @Test
    void aCleanSentenceIsNailed() throws IOException {
        AttemptResult r = AttemptScorer.score(fixture("pa-scripted-very.json"));
        assertThat(r.level()).isEqualTo(AttemptResult.Level.NAILED);
        assertThat(r.focus()).isEmpty();
    }

    @Test
    void aSoftVInVeryIsASoundFocus() {
        AttemptResult r = AttemptScorer.score(List.of(
                word("it", 0, 100, 95), word("is", 100, 200, 95),
                word("very", 200, 500, 62, sound("v", 30), sound("ɛ", 90)), word("good", 500, 800, 95)));
        assertThat(r.focus()).singleElement().satisfies(f -> {
            assertThat(f.word()).isEqualTo("very");
            assertThat(f.pattern()).isEqualTo(SoundPattern.V_BH);
            assertThat(f.kind()).isEqualTo(AttemptResult.Kind.SOUND);
        });
        assertThat(r.level()).isEqualTo(AttemptResult.Level.CLOSER);
    }

    @Test
    void aMissedWordIsAFocus() {
        AttemptResult r = AttemptScorer.score(List.of(word("it", 0, 100, 95),
                new SpokenWord("very", 0, 0, 0, "Omission", List.of()), word("good", 500, 800, 95)));
        assertThat(r.focus()).singleElement().satisfies(f -> assertThat(f.kind()).isEqualTo(AttemptResult.Kind.MISSING));
    }

    @Test
    void nothingHeardIsOnceMore() {
        assertThat(AttemptScorer.score(List.of()).level()).isEqualTo(AttemptResult.Level.ONCE_MORE);
    }

    @Test
    void theDeckHasThreeSentencesForEachPattern() {
        for (SoundPattern p : SoundPattern.values()) assertThat(PracticeDeck.sentences(p)).hasSize(3);
    }

    /** Review #1: an upgrade is about saying it fluently and clearly, not word for word (US-4.4). */
    @Test
    void anUpgradeIgnoresADroppedWord() {
        AttemptResult r = AttemptScorer.score(List.of(word("i", 0, 100, 95),
                new SpokenWord("a", 0, 0, 0, "Omission", List.of()), word("fixed", 100, 400, 95), word("bug", 400, 700, 95)), true);
        assertThat(r.focus()).isEmpty();
        assertThat(r.level()).isEqualTo(AttemptResult.Level.NAILED);
    }

    /** Review #6: re-reading a word is not the "ischool" vowel — only a lone short vowel counts. */
    @Test
    void aRepeatedWordBeforeStationIsNotAnExtraVowel() {
        AttemptResult r = AttemptScorer.score(List.of(word("near", 0, 300, 95), word("the", 300, 400, 95),
                new SpokenWord("the", 400, 500, 95, "Insertion", List.of()), word("station", 500, 900, 95)));
        assertThat(r.focus()).isEmpty();
    }

    /** Review #8: nothing to work on and clear enough is "Nailed it", whatever the accent (US-3.2). */
    @Test
    void clearWithNothingToFixIsNailed() {
        AttemptResult r = AttemptScorer.score(List.of(word("it", 0, 100, 78), word("is", 100, 200, 80), word("good", 200, 500, 76)));
        assertThat(r.level()).isEqualTo(AttemptResult.Level.NAILED);
    }

    /** The words to work on keep the sentence's order, dropped words included. */
    @Test
    void focusFollowsTheSentence() {
        var words = java.util.List.of(
                new SpokenWord("very", 0, 100, 50, "None", java.util.List.of(new SpokenSound("v", 20))),
                new SpokenWord("big", 100, 200, 0, "Omission", java.util.List.of()),
                new SpokenWord("i", 200, 250, 40, "Insertion", java.util.List.of()),
                new SpokenWord("school", 250, 400, 90, "None", java.util.List.of(new SpokenSound("s", 90))));
        assertThat(AttemptScorer.score(words).focus()).extracting(AttemptResult.Focus::word).containsExactly("very", "big", "school");
    }
}
