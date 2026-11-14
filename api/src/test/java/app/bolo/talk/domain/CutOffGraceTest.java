package app.bolo.talk.domain;

import app.bolo.talk.domain.CutOffGrace.Talk;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TLK-10 changed 2026-11-14 (founder): a talk that was cut off doesn't use up a talk when the learner starts again soon. */
class CutOffGraceTest {

    static final Instant T = Instant.parse("2026-11-14T06:00:00Z"); // 12:00 in Dhaka

    static Talk talk(Instant start, Duration length, String reason) {
        return new Talk(start, start.plus(length), reason, true);
    }

    static Instant at(long minutes) {
        return T.plus(Duration.ofMinutes(minutes));
    }

    @Test
    void aCutOffTalkFollowedWithin30MinutesDoesNotCount() {
        var cut = talk(T, Duration.ofMinutes(3), "disconnected");
        var next = talk(at(10), Duration.ofMinutes(5), "time_up");
        assertThat(CutOffGrace.countedStarts(List.of(cut, next), at(60))).containsExactly(next.started());
    }

    @Test
    void aCutOffTalkWithNoNewTalkFor30MinutesCounts() {
        var cut = talk(T, Duration.ofMinutes(3), "disconnected");
        var late = talk(at(3 + 31), Duration.ofMinutes(5), "time_up");
        assertThat(CutOffGrace.countedStarts(List.of(cut, late), at(90))).containsExactlyInAnyOrder(cut.started(), late.started());
    }

    @Test
    void within30MinutesOfTheCutOffItDoesNotCountYetSoTheLearnerCanStartAgain() {
        var cut = talk(T, Duration.ofMinutes(3), "disconnected");
        assertThat(CutOffGrace.countedStarts(List.of(cut), at(3 + 29))).isEmpty();
        assertThat(CutOffGrace.countedStarts(List.of(cut), at(3 + 31))).containsExactly(cut.started());
    }

    @Test
    void bolosVoiceFailingCountsAsCutOff() {
        var cut = talk(T, Duration.ofMinutes(2), "voice_unavailable");
        var next = talk(at(5), Duration.ofMinutes(5), "learner_ended");
        assertThat(CutOffGrace.countedStarts(List.of(cut, next), at(60))).containsExactly(next.started());
    }

    @Test
    void endingItYourselfRunningOutOfTimeOrACrisisAlwaysCounts() {
        for (String reason : List.of("learner_ended", "time_up", "crisis")) {
            var ended = talk(T, Duration.ofMinutes(3), reason);
            var next = talk(at(5), Duration.ofMinutes(5), "time_up");
            assertThat(CutOffGrace.countedStarts(List.of(ended, next), at(60))).as(reason).hasSize(2);
        }
    }

    @Test
    void aTalkStillOpenCounts() {
        var open = new Talk(T, null, null, true);
        assertThat(CutOffGrace.countedStarts(List.of(open), at(1))).containsExactly(T);
    }

    @Test
    void aTalkTheOldRuleDidNotCountStaysUncountedAndStillProvesTheLearnerCameBack() {
        var cut = talk(T, Duration.ofMinutes(3), "disconnected");
        var quickDrop = new Talk(at(5), at(5).plusSeconds(30), "disconnected", false);
        assertThat(CutOffGrace.countedStarts(List.of(cut, quickDrop), at(120))).isEmpty();
    }

    @Test
    void repeatedCutOffsAreForgivenInAChain() {
        var a = talk(T, Duration.ofMinutes(2), "disconnected");
        var b = talk(at(4), Duration.ofMinutes(2), "disconnected");
        var c = talk(at(8), Duration.ofMinutes(5), "time_up");
        assertThat(CutOffGrace.countedStarts(List.of(a, b, c), at(60))).containsExactly(c.started());
    }

    /** The bill: at most 2 cut-off talks a Dhaka day are forgiven; later ones count. */
    @Test
    void atMostTwoCutOffTalksADayAreForgiven() {
        var a = talk(T, Duration.ofMinutes(2), "disconnected");
        var b = talk(at(4), Duration.ofMinutes(2), "disconnected");
        var c = talk(at(8), Duration.ofMinutes(2), "disconnected");
        var d = talk(at(12), Duration.ofMinutes(5), "time_up");
        assertThat(CutOffGrace.countedStarts(List.of(d, c, b, a), at(60))).containsExactlyInAnyOrder(c.started(), d.started());
    }

    @Test
    void theDailyAllowanceStartsAgainOnTheNextDhakaDay() {
        Instant late = Instant.parse("2026-11-14T17:50:00Z"); // 23:50 in Dhaka
        var a = talk(late.minus(Duration.ofMinutes(20)), Duration.ofMinutes(2), "disconnected");
        var b = talk(late.minus(Duration.ofMinutes(10)), Duration.ofMinutes(2), "disconnected");
        var c = talk(late.plus(Duration.ofMinutes(15)), Duration.ofMinutes(2), "disconnected"); // 00:05 next day
        var d = talk(late.plus(Duration.ofMinutes(20)), Duration.ofMinutes(5), "time_up");
        assertThat(CutOffGrace.countedStarts(List.of(a, b, c, d), late.plus(Duration.ofHours(2)))).containsExactly(d.started());
    }
}
