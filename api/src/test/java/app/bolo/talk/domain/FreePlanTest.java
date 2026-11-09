package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-5 / US-8.7: 7 free talks in the first 7 days from the first talk, then 2 a month (months in Asia/Dhaka). */
class FreePlanTest {

    static Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void noTalksYetIsSevenOfSeven() {
        var a = FreePlan.allowance(List.of(), at("2026-11-09T12:00:00Z"));
        assertThat(a).isEqualTo(new FreePlan.Allowance("first_week", 7, 7, null));
    }

    @Test
    void theFirstWeekCountsFromTheFirstTalk() {
        Instant now = at("2026-11-09T12:00:00Z");
        var a = FreePlan.allowance(List.of(now.minus(Duration.ofDays(6)), now.minus(Duration.ofDays(1)), now.minus(Duration.ofHours(1))), now);
        assertThat(a).isEqualTo(new FreePlan.Allowance("first_week", 4, 7, LocalDate.parse("2026-11-10")));
    }

    @Test
    void afterTheFirstWeekItIsTwoAMonth() {
        var a = FreePlan.allowance(List.of(at("2026-10-01T06:00:00Z"), at("2026-11-03T06:00:00Z")), at("2026-11-09T12:00:00Z"));
        assertThat(a).isEqualTo(new FreePlan.Allowance("month", 1, 2, LocalDate.parse("2026-12-01")));
    }

    @Test
    void firstWeekTalksDoNotUseThisMonthsTwo() {
        List<Instant> starts = new ArrayList<>();
        for (int d = 1; d <= 7; d++) starts.add(at("2026-11-0" + d + "T06:00:00Z"));
        var a = FreePlan.allowance(starts, at("2026-11-09T12:00:00Z"));
        assertThat(a).isEqualTo(new FreePlan.Allowance("month", 2, 2, LocalDate.parse("2026-12-01")));
    }

    @Test
    void theMonthTurnsAtMidnightInDhaka() {
        // 17:00Z is 23:00 on 30 November in Dhaka; 18:30Z is 00:30 on 1 December.
        var a = FreePlan.allowance(List.of(at("2026-09-01T06:00:00Z"), at("2026-11-30T17:00:00Z"), at("2026-11-30T18:30:00Z")),
                at("2026-12-02T12:00:00Z"));
        assertThat(a.left()).isEqualTo(1);
    }

    @Test
    void resetsOnIsTheNextFirst() {
        // 19:00Z on 31 December is 01:00 on 1 January in Dhaka.
        var a = FreePlan.allowance(List.of(at("2026-09-01T06:00:00Z")), at("2026-12-31T19:00:00Z"));
        assertThat(a.resetsOn()).isEqualTo(LocalDate.parse("2027-02-01"));
    }
}
