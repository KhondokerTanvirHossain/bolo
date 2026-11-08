package app.bolo.goldenset;

import app.bolo.assessment.domain.SoundPattern;
import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.ScoredPhoneme;
import app.bolo.speech.application.port.in.ScoredWord;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static app.bolo.assessment.domain.SoundPattern.TH_TD;
import static app.bolo.assessment.domain.SoundPattern.V_BH;
import static org.assertj.core.api.Assertions.assertThat;

class AgreementTest {

    static ScoredWord word(String w, double acc, String symbol, double soundAcc) {
        return new ScoredWord(w, 0, 0, acc, "None", List.of(new ScoredPhoneme(symbol, soundAcc)));
    }

    static ScoredWord plain(String w) {
        return new ScoredWord(w, 0, 0, 95, "None", List.of(new ScoredPhoneme("k", 95)));
    }

    static Agreement.Outcome outcome(String id, String reference, Set<GoldenSet.Label> labels, ScoredWord... words) {
        return new Agreement.Outcome(new GoldenSet.Recording(id, Path.of(id + ".wav"), reference, "t", labels),
                new PronunciationResult(true, 80, List.of(words)));
    }

    /** "patternWords": n words carrying the pattern's sound; the first {@code teacher} are teacher issues, the first {@code bolo} weak. */
    static Agreement.Outcome series(String id, SoundPattern p, String symbol, int n, int teacher, int bolo) {
        List<ScoredWord> words = new ArrayList<>();
        Set<GoldenSet.Label> labels = new HashSet<>();
        StringBuilder ref = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String w = "w" + i;
            ref.append(w).append(' ');
            words.add(i < bolo ? word(w, 50, symbol, 20) : word(w, 95, symbol, 95));
            if (i < teacher) labels.add(new GoldenSet.Label(i, p));
        }
        return outcome(id, ref.toString().trim(), labels, words.toArray(ScoredWord[]::new));
    }

    @Test
    void countsAgainstTheTeacher() {
        var a = Agreement.of(List.of(
                outcome("vans", "very few vans visit the village", Set.of(new GoldenSet.Label(0, V_BH)),
                        word("very", 50, "v", 20), plain("few"), word("vans", 95, "v", 95), plain("visit"), plain("the"),
                        word("village", 60, "v", 40)),
                outcome("think", "I think so", Set.of(new GoldenSet.Label(1, TH_TD)),
                        plain("I"), word("think", 70, "θ", 80), plain("so"))), 60, 75, 5);
        var v = a.byPattern().get(V_BH);
        assertThat(List.of(v.tp(), v.fp(), v.fn(), v.tn())).containsExactly(1, 1, 0, 1);
        var th = a.byPattern().get(TH_TD);
        assertThat(List.of(th.tp(), th.fp(), th.fn(), th.tn())).containsExactly(0, 0, 1, 0);
        assertThat(a.disagreements()).extracting(Agreement.Disagreement::word).containsExactlyInAnyOrder("village", "think");
    }

    @Test
    void fewTeacherIssuesIsNotEnoughData() {
        var a = Agreement.of(List.of(series("r", V_BH, "v", 5, 3, 3)), 60, 75, 5);
        assertThat(a.byPattern().get(V_BH).verdict()).isEqualTo(Agreement.Verdict.NOT_ENOUGH_DATA);
        assertThat(a.byPattern().get(V_BH).precision()).isEqualTo(1.0);
        assertThat(a.gate()).isEqualTo(Agreement.GateVerdict.NOT_DECIDED);
    }

    @Test
    void aPatternBelowTheBarFails() {
        var a = Agreement.of(List.of(series("r", V_BH, "v", 8, 5, 2)), 60, 75, 5);
        assertThat(a.byPattern().get(V_BH).recall()).isEqualTo(0.4);
        assertThat(a.byPattern().get(V_BH).verdict()).isEqualTo(Agreement.Verdict.FAIL);
        assertThat(a.gate()).isEqualTo(Agreement.GateVerdict.FAIL);
    }

    @Test
    void fourPassingPatternsPassTheGate() {
        var a = Agreement.of(List.of(series("v", V_BH, "v", 6, 5, 5), series("w", SoundPattern.W_V, "w", 6, 5, 5),
                series("th", TH_TD, "θ", 6, 5, 5), series("z", SoundPattern.Z_J, "z", 6, 5, 5)), 60, 75, 5);
        assertThat(a.gate()).isEqualTo(Agreement.GateVerdict.PASS);
        assertThat(a.rawAgreement()).isEqualTo(1.0);
    }

    @Test
    void nothingHeardIsSkippedNotCountedAsClean() {
        var rec = new GoldenSet.Recording("quiet", Path.of("q.wav"), "very few vans", "t", Set.of(new GoldenSet.Label(0, V_BH)));
        var a = Agreement.of(List.of(new Agreement.Outcome(rec, PronunciationResult.nothingHeard())), 60, 75, 5);
        assertThat(a.skipped()).extracting(Agreement.Skipped::recordingId, Agreement.Skipped::reason).containsExactly(
                org.assertj.core.groups.Tuple.tuple("quiet", "nothing heard"));
        assertThat(a.byPattern().get(V_BH).fn()).isZero();
    }

    @Test
    void wordsThatDoNotMatchTheReferenceAreSkipped() {
        var a = Agreement.of(List.of(outcome("off", "very few vans visit", Set.of(), plain("hello"), plain("there"))), 60, 75, 5);
        assertThat(a.skipped()).extracting(Agreement.Skipped::recordingId).containsExactly("off");
        assertThat(a.skipped().get(0).reason()).contains("0 of 4");
    }

    @Test
    void cleanSpeechReportsTheFalseFlagRate() {
        var a = Agreement.of(List.of(series("clean", V_BH, "v", 10, 0, 1)), 60, 75, 5);
        var v = a.byPattern().get(V_BH);
        assertThat(v.falseFlagRate()).isEqualTo(0.1);
        assertThat(v.recall()).isNull();
        assertThat(v.verdict()).isEqualTo(Agreement.Verdict.NOT_ENOUGH_DATA);
    }

    /** Review I3: a word with two weak sounds counts only the one practice would show. */
    @Test
    void theHarnessCountsWhatLearnersSee() {
        var a = Agreement.of(List.of(outcome("these", "these", Set.of(new GoldenSet.Label(0, SoundPattern.Z_J)),
                new ScoredWord("these", 0, 0, 50, "None", List.of(new ScoredPhoneme("ð", 20), new ScoredPhoneme("z", 30))))), 60, 75, 5);
        assertThat(a.byPattern().get(SoundPattern.Z_J).fn()).isEqualTo(1);
        assertThat(a.byPattern().get(SoundPattern.Z_J).tp()).isZero();
        assertThat(a.byPattern().get(SoundPattern.TH_TD).fp()).isEqualTo(1);
    }

    /** Review I2: with miscues on, Azure returns unspoken reference words as Omissions; a clip of the wrong text is skipped. */
    @Test
    void mostlyOmittedWordsAreSkippedNotMeasured() {
        var a = Agreement.of(List.of(outcome("wrong", "very few vans visit", Set.of(new GoldenSet.Label(0, V_BH)),
                new ScoredWord("very", 0, 0, 0, "Omission", List.of()), new ScoredWord("few", 0, 0, 0, "Omission", List.of()),
                new ScoredWord("vans", 0, 0, 0, "Omission", List.of()), word("visit", 90, "v", 90),
                new ScoredWord("hello", 0, 0, 60, "Insertion", List.of()))), 60, 75, 5);
        assertThat(a.skipped()).extracting(Agreement.Skipped::recordingId).containsExactly("wrong");
        assertThat(a.skipped().get(0).reason()).contains("1 of 4");
        assertThat(a.byPattern().get(V_BH).fn()).isZero();
    }
}
