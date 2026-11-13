package app.bolo.coaching.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** DBF-8: "How was today?" after talks 2, 3 and 4, then every third (7, 10, 13 …); never after talk 1. */
class RatingRuleTest {

    @Test
    void asksAt234ThenEveryThird() {
        for (int n : new int[]{2, 3, 4, 7, 10, 13, 100}) assertThat(RatingService.asksAt(n)).as("talk %d", n).isTrue();
        for (int n : new int[]{0, 1, 5, 6, 8, 9, 11, 12}) assertThat(RatingService.asksAt(n)).as("talk %d", n).isFalse();
    }
}
