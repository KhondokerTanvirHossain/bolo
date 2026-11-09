package app.bolo.learner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-2 / US-6.1: the intro comes back on Day 7, 14, 21, then every 30 days; due for 7 days after it unlocks. */
class IntroScheduleTest {

    static final LocalDate DAY1 = LocalDate.parse("2026-11-04");

    static IntroSchedule.Status at(String today, Integer... recorded) {
        return IntroSchedule.of(DAY1, Set.of(recorded), LocalDate.parse(today));
    }

    @Test
    void beforeDay7NothingIsDue() {
        var s = at("2026-11-09");
        assertThat(s.due()).isNull();
        assertThat(s.unlocked()).isEmpty();
        assertThat(s.nextDay()).isEqualTo(7);
        assertThat(s.nextOn()).isEqualTo(LocalDate.parse("2026-11-10"));
    }

    @Test
    void day7IsDueOnItsDay() {
        var s = at("2026-11-10");
        assertThat(s.due()).isEqualTo(7);
        assertThat(s.unlocked()).containsExactly(7);
        assertThat(s.nextDay()).isEqualTo(14);
    }

    @Test
    void dueLastsSevenDays() {
        assertThat(at("2026-11-16").due()).isEqualTo(7);
        assertThat(at("2026-11-17").due()).isEqualTo(14);
    }

    @Test
    void afterDay21EveryThirtyDays() {
        assertThat(IntroSchedule.unlockDays(5)).containsExactly(7, 14, 21, 51, 81);
        var s = at("2026-12-24", 7, 14, 21);
        assertThat(s.due()).isEqualTo(51);
    }

    @Test
    void aRecordedDayIsNotDue() {
        var s = at("2026-11-11", 7);
        assertThat(s.due()).isNull();
        assertThat(s.nextDay()).isEqualTo(14);
    }

    @Test
    void dayOneIsTheDhakaDate() {
        // 20:00Z on 3 November is 02:00 on 4 November in Dhaka.
        assertThat(IntroSchedule.dayOne(Instant.parse("2026-11-03T20:00:00Z"))).isEqualTo(LocalDate.parse("2026-11-04"));
        assertThat(IntroSchedule.on(LocalDate.parse("2026-11-04"), 7)).isEqualTo(LocalDate.parse("2026-11-10"));
    }
}
