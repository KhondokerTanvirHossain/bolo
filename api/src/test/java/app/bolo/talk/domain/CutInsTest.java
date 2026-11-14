package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** SCN-2, US-7.3 (2026-11-15): when a Realistic standup may cut in. */
class CutInsTest {

    static final CutIns.Config C = CutIns.Config.DEFAULT;

    static CutIns realistic() {
        return CutIns.forTalk("standup", "realistic", false, C);
    }

    @Test
    void theDefaultsAreTheSpecs() {
        assertThat(C).isEqualTo(new CutIns.Config(20_000, 400, 2, 60_000, 30_000));
    }

    @Test
    void neverInTheFirstLearnerTurn() {
        assertThat(realistic().allowed(1, 0, 200_000)).isFalse();
        assertThat(realistic().allowed(2, 0, 200_000)).isTrue();
    }

    @Test
    void atMostTwoAndAMinuteApart() {
        CutIns c = realistic();
        c.used(10_000);
        assertThat(c.allowed(3, 69_999, 200_000)).isFalse();
        assertThat(c.allowed(3, 70_000, 200_000)).isTrue();
        c.used(70_000);
        assertThat(c.allowed(4, 500_000, 200_000)).isFalse();
    }

    @Test
    void neverInTheLast30Seconds() {
        assertThat(realistic().allowed(2, 0, 29_999)).isFalse();
        assertThat(realistic().allowed(2, 0, 30_000)).isTrue();
    }

    @Test
    void neverInFreeTalkFriendlyOrWithIStammer() {
        assertThat(CutIns.forTalk("free", null, false, C).allowed(2, 0, 200_000)).isFalse();
        assertThat(CutIns.forTalk("standup", "friendly", false, C).allowed(2, 0, 200_000)).isFalse();
        assertThat(CutIns.forTalk("standup", "realistic", true, C).allowed(2, 0, 200_000)).isFalse();
    }

    @Test
    void notTheTurnRightAfterAPause() {
        CutIns c = realistic();
        c.skipNext();
        assertThat(c.allowed(2, 0, 200_000)).isFalse();
        assertThat(c.allowed(3, 0, 200_000)).isTrue();
    }
}
