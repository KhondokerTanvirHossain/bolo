package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.ScoredSegment;
import app.bolo.speech.application.port.in.ScoredWord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AzureScoreParserTest {

    static String fixture() throws IOException {
        try (InputStream in = AzureScoreParserTest.class.getResourceAsStream("/audio/pa-unscripted-en-US.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void readsWordsTimesAndPhonemesFromTheRestShape() throws IOException {
        ScoredSegment s = AzureScoreParser.parse(fixture()).orElseThrow();
        assertThat(s.words()).hasSize(11);
        assertThat(s.text()).contains("job interview");
        ScoredWord want = s.words().get(1);
        assertThat(want.word()).isEqualTo("want");
        assertThat(want.startMs()).isEqualTo(200);
        assertThat(want.endMs()).isEqualTo(530);
        ScoredWord interview = s.words().stream().filter(w -> w.word().equals("interview")).findFirst().orElseThrow();
        assertThat(interview.phonemes()).extracting(p -> p.symbol()).containsExactly("ɪ", "n", "t", "ɚ", "v", "ju");
        assertThat(interview.phonemes()).allMatch(p -> p.accuracy() == 100.0);
    }

    @Test
    void readsTheNestedSdkShape() {
        String sdk = """
                {"DisplayText":"Very good.","NBest":[{"Display":"Very good.","Words":[
                  {"Word":"very","Offset":1000000,"Duration":4000000,
                   "PronunciationAssessment":{"AccuracyScore":62.0,"ErrorType":"Mispronunciation"},
                   "Phonemes":[{"Phoneme":"v","PronunciationAssessment":{"AccuracyScore":31.0}},
                               {"Phoneme":"ɛ","PronunciationAssessment":{"AccuracyScore":90.0}}]}]}]}""";
        ScoredWord very = AzureScoreParser.parse(sdk).orElseThrow().words().getFirst();
        assertThat(very.startMs()).isEqualTo(100);
        assertThat(very.endMs()).isEqualTo(500);
        assertThat(very.accuracy()).isEqualTo(62.0);
        assertThat(very.errorType()).isEqualTo("Mispronunciation");
        assertThat(very.phonemes().getFirst().symbol()).isEqualTo("v");
        assertThat(very.phonemes().getFirst().accuracy()).isEqualTo(31.0);
    }

    @Test
    void malformedJsonIsEmpty() {
        assertThat(AzureScoreParser.parse("{not json")).isEmpty();
        assertThat(AzureScoreParser.parse("{\"NBest\":[]}")).isEmpty();
    }
}
