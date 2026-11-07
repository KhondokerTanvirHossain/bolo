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
}
