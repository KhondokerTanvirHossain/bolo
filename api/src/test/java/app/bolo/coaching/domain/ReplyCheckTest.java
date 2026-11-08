package app.bolo.coaching.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyCheckTest {

    @Test
    void aQuoteMustBeWordsTheLearnerReallySaid() {
        assertThat(ReplyCheck.quoteIn("Yesterday I fix bug, it was in payment.", "I fix bug")).isTrue();
        assertThat(ReplyCheck.quoteIn("Yesterday I fix bug, it was in payment.", "i FIX bug,")).isTrue();
        assertThat(ReplyCheck.quoteIn("Yesterday I fix bug, it was in payment.", "I love pizza")).isFalse();
        assertThat(ReplyCheck.quoteIn("I fixed it", "fix")).isFalse();
        assertThat(ReplyCheck.quoteIn("anything", " ")).isFalse();
    }

    @Test
    void anUpgradeStaysShortChangesSomethingAndAvoidsBannedWords() {
        assertThat(ReplyCheck.upgradeOk("I fix bug", "Yesterday I fixed a bug in the payment service.")).isTrue();
        assertThat(ReplyCheck.upgradeOk("I fix bug", "I fix bug.")).isFalse();
        assertThat(ReplyCheck.upgradeOk("x", "one two three four five six seven eight nine ten eleven twelve thirteen "
                + "fourteen fifteen sixteen seventeen eighteen nineteen twenty twentyone twentytwo twentythree "
                + "twentyfour twentyfive twentysix")).isFalse();
        assertThat(ReplyCheck.upgradeOk("I fix bug", "That was a mistake, say I fixed a bug.")).isFalse();
    }

    @Test
    void changedWordsAreTheNewOnes() {
        assertThat(ReplyCheck.changedWords("Yesterday I fix bug", "Yesterday I fixed a bug.")).containsExactly("fixed", "a");
    }

    /** Founder test 2026-11-08: both 5-minute talks failed — a quote that repeats a word ("to … to") broke this. */
    @Test
    void changedWordsWhenTheLearnerRepeatsAWord() {
        assertThat(ReplyCheck.changedWords("I want to go to the the office", "I want to go to the office today."))
                .containsExactly("today");
    }
}
