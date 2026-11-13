package app.bolo.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static app.bolo.assessment.domain.Words.turn;
import static app.bolo.assessment.domain.Words.word;
import static org.assertj.core.api.Assertions.assertThat;

class FluencyStatsTest {

    @Test
    void countsSpeechTimeWordsPausesAndFillers() {
        var t = turn(1, "um I actually like it um you know",
                word("um", 0, 300, 90), word("I", 400, 500, 90), word("actually", 600, 1000, 90),
                word("like", 2500, 2800, 90), word("it", 2900, 3000, 90));
        FluencyStats s = FluencyStats.of(List.of(t));
        assertThat(s.speechMs()).isEqualTo(3000);
        assertThat(s.words()).isEqualTo(8);
        assertThat(s.longPauses()).isEqualTo(1);
        assertThat(s.fillers()).containsEntry("um", 2).containsEntry("actually", 1).containsEntry("you know", 1)
                .doesNotContainKey("like");
        assertThat(s.wordsPerMinute()).isEqualTo(160);
    }

    @Test
    void withoutWordTimesSpeechIsEstimatedFromTheText() {
        FluencyStats s = FluencyStats.of(List.of(new TurnScore(1, "one two three four", List.of())));
        assertThat(s.speechMs()).isEqualTo(4 * 350);
        assertThat(s.averageWordsPerTurn()).isEqualTo(4.0);
    }

    /** AX-8: with I stammer, blocks (gaps over 1.2 s between words) don't pull words per minute down or count as pauses. */
    @Test
    void withStammerLongGapsLeaveWordsPerMinuteAndPausesAlone() {
        var t = turn(1, "I want", word("I", 0, 300, 90), word("want", 3300, 3600, 90));
        FluencyStats plain = FluencyStats.of(List.of(t));
        FluencyStats stammer = FluencyStats.of(List.of(t), true);
        assertThat(plain.wordsPerMinute()).isEqualTo(33);
        assertThat(plain.longPauses()).isEqualTo(1);
        assertThat(stammer.wordsPerMinute()).isEqualTo(200);
        assertThat(stammer.longPauses()).isZero();
        assertThat(stammer.speechMs()).isEqualTo(3600);
    }
}
