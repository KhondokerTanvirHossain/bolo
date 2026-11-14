package app.bolo.reminder.domain;

import app.bolo.reminder.domain.ReminderRules.Decision;
import app.bolo.reminder.domain.ReminderRules.Facts;
import app.bolo.reminder.domain.ReminderRules.Kind;
import app.bolo.reminder.domain.ReminderRules.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-5 / PRG-6: when a reminder goes out, and when Bolo backs off. */
class ReminderRulesTest {

    static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    static final Facts FREE_DAY = new Facts(false, false, true, false);

    static Instant at(LocalDate d, String hhmm, ZoneId z) {
        return LocalDateTime.of(d, LocalTime.parse(hhmm)).atZone(z).toInstant();
    }

    static Instant at(String hhmm) {
        return at(DAY, hhmm, DHAKA);
    }

    static State fresh() {
        return new State(true, LocalTime.of(18, 30), DHAKA, null, null, false, 0, null, false);
    }

    /** Yesterday's reminder went out at 18:30; tapped or not. */
    static State sentYesterday(boolean tapped, int ignored) {
        return new State(true, LocalTime.of(18, 30), DHAKA, DAY.minusDays(1), at(DAY.minusDays(1), "18:30", DHAKA), tapped, ignored, null, false);
    }

    @Test
    void offNothing() {
        State off = new State(false, LocalTime.of(18, 30), DHAKA, null, null, false, 0, null, false);
        assertThat(ReminderRules.decide(off, FREE_DAY, at("18:30")).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void beforeTimeNothing() {
        assertThat(ReminderRules.decide(fresh(), FREE_DAY, at("18:29")).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void atTimeNormal() {
        Decision d = ReminderRules.decide(fresh(), FREE_DAY, at("18:30"));
        assertThat(d.kind()).isEqualTo(Kind.NORMAL);
        assertThat(d.day()).isEqualTo(DAY);
        assertThat(d.ignored()).isZero();
    }

    @Test
    void missedWindowSendsNothing() {
        assertThat(ReminderRules.decide(fresh(), FREE_DAY, at("20:29")).kind()).isEqualTo(Kind.NORMAL);
        assertThat(ReminderRules.decide(fresh(), FREE_DAY, at("20:30")).kind()).isEqualTo(Kind.NONE);
        assertThat(ReminderRules.decide(fresh(), FREE_DAY, at("23:59")).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void alreadySentToday() {
        State s = new State(true, LocalTime.of(18, 30), DHAKA, DAY, at("18:30"), false, 0, null, false);
        assertThat(ReminderRules.decide(s, FREE_DAY, at("18:45")).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void talkedTodayNothing() {
        assertThat(ReminderRules.decide(fresh(), new Facts(true, true, true, false), at("18:30")).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void noTalkAvailableNothing() {
        assertThat(ReminderRules.decide(fresh(), new Facts(false, false, false, false), at("18:30")).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void talksBackLine() {
        assertThat(ReminderRules.decide(fresh(), new Facts(false, false, true, true), at("18:30")).kind()).isEqualTo(Kind.TALKS_BACK);
    }

    @Test
    void thirdIgnoredStops() {
        Decision second = ReminderRules.decide(sentYesterday(false, 1), FREE_DAY, at("18:30"));
        assertThat(second.kind()).isEqualTo(Kind.NORMAL);
        assertThat(second.ignored()).isEqualTo(2);
        Decision third = ReminderRules.decide(sentYesterday(false, 2), FREE_DAY, at("18:30"));
        assertThat(third.kind()).isEqualTo(Kind.NONE);
        assertThat(third.stop()).isTrue();
        assertThat(third.ignored()).isEqualTo(3);
    }

    @Test
    void tappedResetsIgnored() {
        Decision d = ReminderRules.decide(sentYesterday(true, 2), FREE_DAY, at("18:30"));
        assertThat(d.kind()).isEqualTo(Kind.NORMAL);
        assertThat(d.ignored()).isZero();
    }

    @Test
    void talkSinceResetsIgnored() {
        Decision d = ReminderRules.decide(sentYesterday(false, 2), new Facts(false, true, true, false), at("18:30"));
        assertThat(d.kind()).isEqualTo(Kind.NORMAL);
        assertThat(d.ignored()).isZero();
        assertThat(d.stop()).isFalse();
    }

    @Test
    void stoppedSilentUntilDaySeven() {
        Instant stopped = at("18:30");
        State s = new State(true, LocalTime.of(18, 30), DHAKA, DAY.minusDays(1), at(DAY.minusDays(1), "18:30", DHAKA), false, 3, stopped, false);
        assertThat(ReminderRules.decide(s, FREE_DAY, at(DAY.plusDays(6), "18:30", DHAKA)).kind()).isEqualTo(Kind.NONE);
        Decision seven = ReminderRules.decide(s, FREE_DAY, at(DAY.plusDays(7), "18:30", DHAKA));
        assertThat(seven.kind()).isEqualTo(Kind.COMEBACK);
        assertThat(seven.day()).isEqualTo(DAY.plusDays(7));
        State after = new State(true, LocalTime.of(18, 30), DHAKA, DAY.plusDays(7), at(DAY.plusDays(7), "18:30", DHAKA), false, 3, stopped, true);
        assertThat(ReminderRules.decide(after, FREE_DAY, at(DAY.plusDays(8), "18:30", DHAKA)).kind()).isEqualTo(Kind.NONE);
        assertThat(ReminderRules.decide(after, FREE_DAY, at(DAY.plusDays(30), "18:30", DHAKA)).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void lateTimeCrossesMidnightOnce() {
        ZoneId ny = ZoneId.of("America/New_York");
        State s = new State(true, LocalTime.of(23, 30), ny, null, null, false, 0, null, false);
        Decision d = ReminderRules.decide(s, FREE_DAY, at(DAY.plusDays(1), "00:45", ny));
        assertThat(d.kind()).isEqualTo(Kind.NORMAL);
        assertThat(d.day()).isEqualTo(DAY);
        State sent = new State(true, LocalTime.of(23, 30), ny, DAY, at(DAY.plusDays(1), "00:45", ny), false, 0, null, false);
        assertThat(ReminderRules.decide(sent, FREE_DAY, at(DAY.plusDays(1), "01:15", ny)).kind()).isEqualTo(Kind.NONE);
        assertThat(ReminderRules.decide(sent, FREE_DAY, at(DAY.plusDays(1), "23:30", ny)).kind()).isEqualTo(Kind.NORMAL);
    }

    @Test
    void windowDayFollowsTheZone() {
        State s = fresh();
        assertThat(ReminderRules.windowDay(s, at("19:00"))).contains(DAY);
        assertThat(ReminderRules.windowDay(s, at("12:00"))).isEmpty();
    }
}
