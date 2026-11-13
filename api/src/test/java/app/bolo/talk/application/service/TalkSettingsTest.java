package app.bolo.talk.application.service;

import app.bolo.talk.domain.TurnPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TalkSettingsTest {

    final TalkSettings settings = new TalkSettings(new TurnPolicy(1_000, 2_500, 4_000, Set.of("and")), "fake", 100, 60_000,
            60_000, 20_000, 15_000, 3_000, 8_000, 15_000, 45_000, 1_800_000);

    @Test
    void pacePicksTheSpeedAndAppliesTheStammerFloor() {
        assertThat(settings.pace("relaxed", false).encourageMs()).isEqualTo(30_000);
        assertThat(settings.pace("relaxed", false).policy().endAfterMs()).isEqualTo(3_500);
        assertThat(settings.pace("quick", false).policy().endAfterMs()).isEqualTo(1_500);
        assertThat(settings.pace(null, false)).isEqualTo(settings.pace("normal", false));
        assertThat(settings.pace("normal", false).encourageMs()).isEqualTo(8_000);
        assertThat(settings.pace("normal", false).policy().endAfterMs()).isEqualTo(2_500);
        assertThat(settings.pace("quick", true).policy().endAfterMs()).isEqualTo(4_500);
        assertThat(settings.pace("quick", true).encourageMs()).isEqualTo(16_000);
        assertThat(settings.pace("relaxed", true).pauseMs()).isEqualTo(120_000);
        assertThat(settings.pace("quick", false).policy().unfinishedWords()).containsExactly("and");
    }
}
