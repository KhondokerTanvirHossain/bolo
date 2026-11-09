package app.bolo.talk.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * The free plan (ACC-5, US-8.7): 7 talks in the first 7 days, counted from the first talk, then 2 a month. Months start on
 * the 1st at 00:00 in Bangladesh. Unused first-week talks don't carry over: once the week ends, that month's 2 are there.
 */
public final class FreePlan {

    public static final int FIRST_WEEK_TALKS = 7;
    public static final int MONTH_TALKS = 2;
    public static final Duration FIRST_WEEK = Duration.ofDays(7);
    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");
    /** A free talk is one 5-minute conversation plus its debrief. */
    public static final int FREE_MINUTES = 5;

    /** window: "first_week" or "month". resetsOn: the Bangladesh date talks come back; null before the first talk. */
    public record Allowance(String window, int left, int total, LocalDate resetsOn) {
    }

    private FreePlan() {
    }

    /** @param countedStarts when each counted talk started (any order) */
    public static Allowance allowance(List<Instant> countedStarts, Instant now) {
        if (countedStarts.isEmpty()) return new Allowance("first_week", FIRST_WEEK_TALKS, FIRST_WEEK_TALKS, null);
        Instant first = countedStarts.stream().min(Comparator.naturalOrder()).orElseThrow();
        // The week ends at midnight in Dhaka, 7 days after the first talk's day: "back on 23 November" holds all day.
        Instant weekEnd = first.atZone(ZONE).toLocalDate().plusDays(FIRST_WEEK.toDays()).atStartOfDay(ZONE).toInstant();
        if (now.isBefore(weekEnd)) {
            int left = Math.max(0, FIRST_WEEK_TALKS - countedStarts.size());
            return new Allowance("first_week", left, FIRST_WEEK_TALKS, weekEnd.atZone(ZONE).toLocalDate());
        }
        LocalDate monthStart = now.atZone(ZONE).toLocalDate().withDayOfMonth(1);
        Instant monthFrom = monthStart.atStartOfDay(ZONE).toInstant();
        Instant from = monthFrom.isAfter(weekEnd) ? monthFrom : weekEnd;
        long used = countedStarts.stream().filter(s -> !s.isBefore(from)).count();
        return new Allowance("month", (int) Math.max(0, MONTH_TALKS - used), MONTH_TALKS, monthStart.plusMonths(1));
    }
}
