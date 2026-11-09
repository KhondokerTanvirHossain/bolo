package app.bolo.home.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-4 / US-6.4: a gentle run; 1 rest day per Saturday-to-Friday week (Asia/Dhaka); a quiet "welcome back". */
class StreakTest {

    static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    /** Thursday 12 November 2026, noon in Dhaka. The week is Sat 7 to Fri 13 November. */
    static final Instant NOW = LocalDate.parse("2026-11-12").atTime(12, 0).atZone(DHAKA).toInstant();

    static List<Instant> on(String... dates) {
        return Arrays.stream(dates).map(d -> LocalDate.parse(d).atTime(10, 0).atZone(DHAKA).toInstant()).toList();
    }

    static String state(Streak.Week w, String date) {
        return w.days().stream().filter(d -> d.date().equals(LocalDate.parse(date))).findFirst().orElseThrow().state();
    }

    @Test
    void noActivityIsNoRunAndNoWelcomeBack() {
        var w = Streak.of(List.of(), NOW);
        assertThat(w.run()).isZero();
        assertThat(w.welcomeBack()).isFalse();
        assertThat(w.restUsed()).isFalse();
    }

    @Test
    void consecutiveDaysCount() {
        var w = Streak.of(on("2026-11-10", "2026-11-11", "2026-11-12"), NOW);
        assertThat(w.run()).isEqualTo(3);
        assertThat(w.restUsed()).isFalse();
        assertThat(state(w, "2026-11-12")).isEqualTo("done");
    }

    @Test
    void todayWithoutActivityIsOpenNotMissed() {
        var w = Streak.of(on("2026-11-10", "2026-11-11"), NOW);
        assertThat(w.run()).isEqualTo(2);
        assertThat(state(w, "2026-11-12")).isEqualTo("today");
        assertThat(w.welcomeBack()).isFalse();
    }

    @Test
    void oneMissInAWeekIsARestDay() {
        var w = Streak.of(on("2026-11-07", "2026-11-08", "2026-11-10", "2026-11-11"), NOW);
        assertThat(w.run()).isEqualTo(4);
        assertThat(w.restUsed()).isTrue();
        assertThat(state(w, "2026-11-09")).isEqualTo("rest");
    }

    @Test
    void twoMissesInAWeekEndTheRun() {
        var w = Streak.of(on("2026-11-07", "2026-11-10", "2026-11-11"), NOW);
        assertThat(w.run()).isEqualTo(2);
        assertThat(w.restUsed()).isFalse();
        assertThat(state(w, "2026-11-09")).isEqualTo("missed");
    }

    @Test
    void restIsOncePerSaturdayWeek() {
        // Fri 6 Nov (last week) and Sat 7 Nov (this week) are both missed: one rest in each week.
        var w = Streak.of(on("2026-11-05", "2026-11-08", "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12"), NOW);
        assertThat(w.run()).isEqualTo(6);
        assertThat(w.restUsed()).isTrue();
        assertThat(state(w, "2026-11-07")).isEqualTo("rest");
    }

    @Test
    void activityCountsOnTheDhakaDate() {
        // 19:30Z on 11 November is 01:30 on 12 November in Dhaka.
        var w = Streak.of(List.of(Instant.parse("2026-11-11T19:30:00Z")), NOW);
        assertThat(state(w, "2026-11-12")).isEqualTo("done");
        assertThat(w.run()).isEqualTo(1);
    }

    @Test
    void theStripIsSaturdayToFriday() {
        var w = Streak.of(on("2026-11-11"), NOW);
        assertThat(w.days()).extracting(Streak.Day::date).containsExactly(
                LocalDate.parse("2026-11-07"), LocalDate.parse("2026-11-08"), LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-11-11"), LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-13"));
        assertThat(state(w, "2026-11-13")).isEqualTo("future");
    }

    @Test
    void aBrokenRunSaysWelcomeBack() {
        var w = Streak.of(on("2026-11-03", "2026-11-04"), NOW);
        assertThat(w.run()).isZero();
        assertThat(w.welcomeBack()).isTrue();
    }
}
