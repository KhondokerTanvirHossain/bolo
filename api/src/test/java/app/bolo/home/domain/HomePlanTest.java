package app.bolo.home.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HomePlanTest {

    static Optional<HomePlan.LastDebrief> last(String status, boolean viewed, String pattern) {
        return Optional.of(new HomePlan.LastDebrief("t1", status, viewed, pattern, pattern == null ? null : "The v sound"));
    }

    @Test
    void aReadyUnviewedDebriefAsksToFinishIt() {
        assertThat(HomePlan.card(last("ready", false, "V_BH"))).contains(new HomePlan.Card("feedback", "t1", null, null, null));
    }

    @Test
    void aViewedDebriefWithASoundFixOffersThatSound() {
        assertThat(HomePlan.card(last("ready", true, "V_BH"))).contains(new HomePlan.Card("practice", null, "V_BH", "The v sound", null));
        assertThat(HomePlan.card(last("ready", true, null))).isEmpty();
    }

    @Test
    void scoringFailedAndTooShortOfferNothingFromFeedback() {
        for (String status : new String[]{"scoring", "failed", "too_short"}) {
            assertThat(HomePlan.card(last(status, false, "V_BH"))).as(status).isEmpty();
        }
    }

    @Test
    void noTalkNoCard() {
        assertThat(HomePlan.card(Optional.empty())).isEmpty();
    }

    /** P1g / US-6.9: Finish your feedback → Re-record your intro → practice. */
    @Test
    void anUnseenFeedbackComesBeforeTheIntro() {
        assertThat(HomePlan.card(last("ready", false, "V_BH"), 7)).contains(new HomePlan.Card("feedback", "t1", null, null, null));
    }

    @Test
    void anIntroDueComesBeforePractice() {
        assertThat(HomePlan.card(last("ready", true, "V_BH"), 7)).contains(new HomePlan.Card("intro", null, null, null, 7));
        assertThat(HomePlan.card(Optional.empty(), 14)).contains(new HomePlan.Card("intro", null, null, null, 14));
    }
}
