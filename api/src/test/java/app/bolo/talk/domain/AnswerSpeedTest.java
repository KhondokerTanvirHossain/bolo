package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AX-7 (2026-11-14): Automatic picks from the median start delay; fewer than 5 answers change nothing. */
class AnswerSpeedTest {

    @Test
    void fewerThanFiveAnswersPickNothing() {
        assertThat(AnswerSpeed.pick(List.of(100, 100, 100, 100))).isEmpty();
    }

    @Test
    void theMedianPicksTheSpeed() {
        assertThat(AnswerSpeed.pick(List.of(2999, 2999, 2999, 9000, 0))).contains("quick");
        assertThat(AnswerSpeed.pick(List.of(3000, 3000, 3000, 1, 1))).contains("normal");
        assertThat(AnswerSpeed.pick(List.of(6000, 6000, 6000, 1, 1))).contains("normal");
        assertThat(AnswerSpeed.pick(List.of(6001, 6001, 6001, 1, 1))).contains("relaxed");
    }

    @Test
    void anEvenCountUsesTheMeanOfTheMiddleTwo() {
        assertThat(AnswerSpeed.pick(List.of(2000, 2000, 2000, 4002, 9000, 9000))).contains("normal");
        assertThat(AnswerSpeed.pick(List.of(2000, 2000, 2000, 3990, 9000, 9000))).contains("quick");
    }
}
