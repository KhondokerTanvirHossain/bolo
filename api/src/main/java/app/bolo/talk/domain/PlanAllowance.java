package app.bolo.talk.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a learner may still talk (ACC-5): an active paid plan gives its monthly talk count for the Dhaka calendar month
 * (counting talks since the plan began); without one, the free plan ({@link FreePlan}). Plans differ only by talk count.
 */
public final class PlanAllowance {

    /** A plan held from {@code startsAt} until {@code endsAt} (exclusive). */
    public record Sub(String plan, Instant startsAt, Instant endsAt) {
    }

    /** plan: free, basic, premium or ultimate; window: first_week or month. */
    public record Allowance(String plan, String window, int left, int total, LocalDate resetsOn) {
    }

    private PlanAllowance() {
    }

    public static Optional<Sub> active(List<Sub> subs, Map<String, Integer> monthly, Instant now) {
        return subs.stream().filter(s -> !s.startsAt().isAfter(now) && now.isBefore(s.endsAt()) && monthly.containsKey(s.plan()))
                .max(Comparator.comparingInt(s -> monthly.get(s.plan())));
    }

    /** Review (P2d): same-plan months joined end-to-start are one stretch; its talks count from the first one's start. */
    static Instant chainStart(Sub sub, List<Sub> subs) {
        Instant start = sub.startsAt();
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Sub s : subs) {
                if (s.plan().equals(sub.plan()) && s.startsAt().isBefore(start) && !s.endsAt().isBefore(start)) {
                    start = s.startsAt();
                    moved = true;
                }
            }
        }
        return start;
    }

    public static Allowance of(List<Instant> countedStarts, List<Sub> subs, Map<String, Integer> monthly, Instant now) {
        Optional<Sub> sub = active(subs, monthly, now);
        if (sub.isEmpty()) {
            var free = FreePlan.allowance(countedStarts, now);
            return new Allowance("free", free.window(), free.left(), free.total(), free.resetsOn());
        }
        LocalDate monthStart = now.atZone(FreePlan.ZONE).toLocalDate().withDayOfMonth(1);
        Instant monthFrom = monthStart.atStartOfDay(FreePlan.ZONE).toInstant();
        Instant start = chainStart(sub.get(), subs);
        Instant from = start.isAfter(monthFrom) ? start : monthFrom;
        long used = countedStarts.stream().filter(s -> !s.isBefore(from)).count();
        int total = monthly.get(sub.get().plan());
        return new Allowance(sub.get().plan(), "month", (int) Math.max(0, total - used), total, monthStart.plusMonths(1));
    }
}
