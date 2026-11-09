package app.bolo.home.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The gentle run (PRG-4, US-6.4). An active day (Asia/Dhaka) has a completed loop or a practice attempt. Counting back
 * from today, a missed day is forgiven once per Saturday-to-Friday week; a second miss in a week ends the run. Today
 * without activity is still open, never a miss. A learner with activity but no run is welcomed back, without guilt.
 */
public final class Streak {

    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    /** state: done, rest, missed, today (not yet active) or future. */
    public record Day(LocalDate date, String state) {
    }

    /** run: active days in the current run; restUsed: this week's rest day is part of the run. */
    public record Week(int run, boolean restUsed, boolean welcomeBack, List<Day> days) {
    }

    private Streak() {
    }

    public static Week of(Collection<Instant> activity, Instant now) {
        TreeSet<LocalDate> active = new TreeSet<>();
        for (Instant a : activity) active.add(a.atZone(ZONE).toLocalDate());
        LocalDate today = now.atZone(ZONE).toLocalDate();
        int run = 0;
        List<LocalDate> rests = new ArrayList<>();
        LocalDate earliestInRun = null;
        if (active.contains(today)) {
            run++;
            earliestInRun = today;
        }
        Set<LocalDate> restWeeks = new HashSet<>();
        LocalDate first = active.isEmpty() ? null : active.first();
        for (LocalDate d = today.minusDays(1); first != null && !d.isBefore(first); d = d.minusDays(1)) {
            if (active.contains(d)) {
                run++;
                earliestInRun = d;
            } else if (restWeeks.add(weekStart(d))) {
                rests.add(d);
            } else {
                break;
            }
        }
        // A rest day only belongs to the run when an active day of the run lies before it.
        LocalDate from = earliestInRun;
        rests.removeIf(r -> from == null || r.isBefore(from));
        LocalDate weekStart = weekStart(today);
        boolean restUsed = rests.stream().anyMatch(r -> !r.isBefore(weekStart));
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            String state = active.contains(d) ? "done" : rests.contains(d) ? "rest" : d.equals(today) ? "today"
                    : d.isAfter(today) ? "future" : "missed";
            days.add(new Day(d, state));
        }
        return new Week(run, restUsed, run == 0 && !active.isEmpty(), days);
    }

    static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }
}
