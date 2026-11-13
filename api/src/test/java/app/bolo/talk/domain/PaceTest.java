package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaceTest {

    static final Pace QUICK = new Pace(5_000, 10_000, 30_000, new TurnPolicy(700, 1_500, 3_000, Set.of("and")));
    static final Pace RELAXED = new Pace(30_000, 45_000, 120_000, new TurnPolicy(1_500, 3_500, 5_500, Set.of("and")));
    static final Pace FLOOR = new Pace(16_000, 30_000, 90_000, new TurnPolicy(1_500, 4_500, 6_500, Set.of()));

    @Test
    void atLeastTakesTheLargerValueEach() {
        Pace q = QUICK.atLeast(FLOOR);
        assertThat(q).isEqualTo(new Pace(16_000, 30_000, 90_000, new TurnPolicy(1_500, 4_500, 6_500, Set.of("and"))));
        Pace r = RELAXED.atLeast(FLOOR);
        assertThat(r).isEqualTo(new Pace(30_000, 45_000, 120_000, new TurnPolicy(1_500, 4_500, 6_500, Set.of("and"))));
    }
}
