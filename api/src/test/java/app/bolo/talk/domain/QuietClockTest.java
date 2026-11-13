package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuietClockTest {

    @Test
    void stagesFireOnceEachAt8And15And45Seconds() {
        QuietClock q = new QuietClock(8_000, 15_000, 45_000);
        assertThat(q.onQuiet(7_980)).isEqualTo(QuietClock.Stage.NONE);
        assertThat(q.onQuiet(20)).isEqualTo(QuietClock.Stage.ENCOURAGE);
        assertThat(q.onQuiet(20)).isEqualTo(QuietClock.Stage.NONE);
        assertThat(q.onQuiet(6_980)).isEqualTo(QuietClock.Stage.HINT);
        assertThat(q.onQuiet(30_000)).isEqualTo(QuietClock.Stage.PAUSE);
        assertThat(q.onQuiet(10_000)).isEqualTo(QuietClock.Stage.NONE);
    }

    @Test
    void speechStartsItAgain() {
        QuietClock q = new QuietClock(8_000, 15_000, 45_000);
        q.onQuiet(7_000);
        q.onSpeech();
        assertThat(q.onQuiet(7_000)).isEqualTo(QuietClock.Stage.NONE);
        assertThat(q.onQuiet(1_000)).isEqualTo(QuietClock.Stage.ENCOURAGE);
    }

    @Test
    void quietMsCountsAndResetsOnSpeech() {
        QuietClock q = new QuietClock(8_000, 15_000, 45_000);
        q.onQuiet(3_000);
        assertThat(q.quietMs()).isEqualTo(3_000);
        q.onSpeech();
        assertThat(q.quietMs()).isZero();
    }
}
