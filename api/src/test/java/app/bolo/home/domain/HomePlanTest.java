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
        assertThat(HomePlan.card(last("ready", false, "V_BH"))).contains(new HomePlan.Card("feedback", "t1", null, null));
    }

    @Test
    void aViewedDebriefWithASoundFixOffersThatSound() {
        assertThat(HomePlan.card(last("ready", true, "V_BH"))).contains(new HomePlan.Card("practice", null, "V_BH", "The v sound"));
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
}
