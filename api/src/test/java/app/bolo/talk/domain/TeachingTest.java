package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The English teacher hat (2026-11-16): when Bolo teaches and how a retry is judged. */
class TeachingTest {

    @Test
    void neverTheFirstTurnAndAtLeastThreeTurnsApart() {
        Teaching t = new Teaching();
        assertThat(t.mayTeach(1)).isFalse();
        assertThat(t.mayTeach(2)).isTrue();
        t.taught(2, "I need to tell the client.");
        assertThat(t.mayTeach(3)).as("the retry turn").isFalse();
        t.drop();
        assertThat(t.mayTeach(4)).isFalse();
        assertThat(t.mayTeach(5)).isTrue();
    }

    @Test
    void atMostFourATalk() {
        Teaching t = new Teaching();
        for (int turn : new int[]{2, 5, 8, 11}) {
            assertThat(t.mayTeach(turn)).isTrue();
            t.taught(turn, "x y z");
            t.drop();
        }
        assertThat(t.mayTeach(14)).isFalse();
    }

    @Test
    void aPendingRetryIsHeldUntilDropped() {
        Teaching t = new Teaching();
        t.taught(2, "I need to tell the client.");
        assertThat(t.pending()).contains("I need to tell the client.");
        t.drop();
        assertThat(t.pending()).isEmpty();
    }

    @Test
    void judgingComparesTheWordsInOrder() {
        String better = "I need to tell the client the release is delayed.";
        assertThat(Teaching.judge("I need to tell the client the release is delayed", better)).isEqualTo(Teaching.Result.NAILED);
        assertThat(Teaching.judge("I need to tell client release delayed", better)).isEqualTo(Teaching.Result.CLOSER);
        assertThat(Teaching.judge("the client is waiting", better)).isEqualTo(Teaching.Result.GOOD_TRY);
    }

    @Test
    void repeatedWordsDontCountAgainstTheTry() {
        assertThat(Teaching.judge("I I need need to to tell the the client", "I need to tell the client.")).isEqualTo(Teaching.Result.NAILED);
    }

    @Test
    void theLinesAreKind() {
        assertThat(Teaching.Result.NAILED.line()).isEqualTo("Nailed it!");
        assertThat(Teaching.Result.CLOSER.line()).isEqualTo("Closer! Nice.");
        assertThat(Teaching.Result.GOOD_TRY.line()).isEqualTo("Good try.");
        assertThat(Teaching.Result.GOOD_TRY.dbValue()).isEqualTo("good_try");
    }

    @Test
    void theChangedWordsAreTheNewOnes() {
        assertThat(Teaching.changed("I have to tell client release is delay", "I need to tell the client the release is delayed."))
                .containsExactly("need", "the", "delayed");
    }

    @Test
    void theTeachLine() {
        assertThat(Teaching.line("I need to tell the client.")).isEqualTo("A smoother way to say that: \"I need to tell the client.\" Try saying it.");
    }
}
