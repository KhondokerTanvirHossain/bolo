package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static app.bolo.talk.domain.TurnTaker.Decision.COMMIT;
import static app.bolo.talk.domain.TurnTaker.Decision.NONE;
import static app.bolo.talk.domain.TurnTaker.Decision.RESUMED;
import static app.bolo.talk.domain.TurnTaker.Decision.SPECULATE;
import static org.assertj.core.api.Assertions.assertThat;

class TurnTakerTest {

    static final TurnPolicy POLICY = new TurnPolicy(1000, 2500, 4000,
            Set.of("and", "but", "because", "so", "or", "then", "like", "um", "uh", "the", "a", "an", "to", "my", "i"));

    record At(long ms, TurnTaker.Decision decision) {
    }

    /** Feeds 20 ms frames: speech while {@code speaking} says so, and records every non-NONE decision. */
    static List<At> run(TurnTaker t, long fromMs, long toMs, boolean speech) {
        List<At> out = new ArrayList<>();
        for (long ms = fromMs; ms < toMs; ms += 20) {
            TurnTaker.Decision d = t.onFrame(speech, ms);
            if (d != NONE) out.add(new At(ms, d));
        }
        return out;
    }

    @Test
    void commitsAfter2500msOfSilenceOnceSomethingWasSaid() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 1000, true);
        t.onTranscript("I work in a bank");
        List<At> decisions = run(t, 1000, 6000, false);
        assertThat(decisions).containsExactly(new At(2000, SPECULATE), new At(3500, COMMIT));
    }

    @Test
    void waitsLongerWhenTheSentenceSoundsUnfinished() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 1000, true);
        t.onTranscript("I went there because");
        List<At> decisions = run(t, 1000, 6000, false);
        assertThat(decisions).containsExactly(new At(2000, SPECULATE), new At(5000, COMMIT));
    }

    @Test
    void speechAfterSpeculationReportsResumed() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 1000, true);
        t.onTranscript("I like cricket");
        assertThat(run(t, 1000, 2100, false)).containsExactly(new At(2000, SPECULATE));
        assertThat(t.onFrame(true, 2100)).isEqualTo(RESUMED);
        run(t, 2120, 2500, true);
        assertThat(run(t, 2500, 3600, false)).containsExactly(new At(3500, SPECULATE));
    }

    @Test
    void silenceAloneNeverCommits() {
        TurnTaker t = new TurnTaker(POLICY);
        assertThat(run(t, 0, 60_000, false)).isEmpty();
    }

    @Test
    void doneCommitsAtOnceButNotWhenNothingWasSaid() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 500, true);
        t.onTranscript("Hello there");
        assertThat(t.onDone(600)).isEqualTo(COMMIT);
        t.reset();
        assertThat(t.onDone(700)).isEqualTo(NONE);
    }

    @Test
    void resetStartsAFreshTurn() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 1000, true);
        t.onTranscript("I work in a bank");
        assertThat(run(t, 1000, 3600, false)).contains(new At(3500, COMMIT));
        t.reset();
        assertThat(run(t, 3600, 20_000, false)).isEmpty();
    }

    /** Speech the recogniser made no words of (Bangla, mumbling): the turn ends as UNCLEAR instead of hanging. */
    @Test
    void speechWithoutWordsIsUnclear() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 1000, true);
        assertThat(run(t, 1000, 6000, false)).containsExactly(new At(3500, TurnTaker.Decision.UNCLEAR));
    }

    /** A cough or a door is not a turn: under 0.8 s of wordless sound stays quiet. */
    @Test
    void aShortWordlessSoundIsNotATurn() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 200, true);
        assertThat(run(t, 200, 6000, false)).isEmpty();
    }

    // SCN-2 (2026-11-15): Realistic standup cuts in at the learner's next breath after 20 s of speech.
    @Test
    void armedCutInEndsALongTurnAtAShortBreath() {
        TurnTaker t = new TurnTaker(POLICY);
        t.armCutIn(20_000, 400);
        run(t, 0, 20_000, true);
        t.onTranscript("Yesterday I finished the payment retry and today");
        assertThat(run(t, 20_000, 20_500, false)).containsExactly(new At(20_400, TurnTaker.Decision.CUT_IN));
    }

    @Test
    void cutInWaitsFor20sOfSpeech() {
        TurnTaker t = new TurnTaker(POLICY);
        t.armCutIn(20_000, 400);
        run(t, 0, 19_900, true);
        t.onTranscript("Yesterday I finished the payment retry");
        List<At> d = run(t, 19_900, 24_000, false);
        assertThat(d).doesNotContain(new At(20_300, TurnTaker.Decision.CUT_IN)).contains(new At(22_400, COMMIT));
        assertThat(d.stream().map(At::decision)).doesNotContain(TurnTaker.Decision.CUT_IN);
    }

    @Test
    void unarmedNeverCutsIn() {
        TurnTaker t = new TurnTaker(POLICY);
        run(t, 0, 21_000, true);
        t.onTranscript("Yesterday I finished the payment retry");
        assertThat(run(t, 21_000, 24_000, false).stream().map(At::decision)).doesNotContain(TurnTaker.Decision.CUT_IN);
        TurnTaker d = new TurnTaker(POLICY);
        d.armCutIn(20_000, 400);
        d.disarmCutIn();
        run(d, 0, 21_000, true);
        d.onTranscript("Yesterday I finished the payment retry");
        assertThat(run(d, 21_000, 24_000, false).stream().map(At::decision)).doesNotContain(TurnTaker.Decision.CUT_IN);
    }
}
