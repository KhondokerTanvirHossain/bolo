package app.bolo.learner.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * When the learner records their intro again (PRG-2, US-6.1): Day 7, 14 and 21, then every 30 days (51, 81, …), counted
 * from Day 1, the Asia/Dhaka date they signed up and recorded their first intro. The latest unlocked day is due for 7 days,
 * unless already recorded; after that it waits for the next one, so nothing nags.
 */
public final class IntroSchedule {

    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");
    public static final int DUE_FOR_DAYS = 7;

    /** due: the day to record now, or null; nextDay/nextOn: the next unlock after today. */
    public record Status(LocalDate day1, List<Integer> unlocked, Integer due, int nextDay, LocalDate nextOn) {
    }

    private IntroSchedule() {
    }

    public static LocalDate dayOne(Instant signedUp) {
        return signedUp.atZone(ZONE).toLocalDate();
    }

    /** The date of Day {@code n}: Day 1 is {@code day1} itself. */
    public static LocalDate on(LocalDate day1, int n) {
        return day1.plusDays(n - 1L);
    }

    /** The first {@code count} unlock days: 7, 14, 21, 51, 81, … */
    public static List<Integer> unlockDays(int count) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; out.size() < count; i++) out.add(i < 3 ? 7 * (i + 1) : 21 + 30 * (i - 2));
        return out;
    }

    public static boolean isUnlockDay(int day) {
        return day == 7 || day == 14 || day == 21 || (day > 21 && (day - 21) % 30 == 0);
    }

    public static Status of(LocalDate day1, Set<Integer> recorded, LocalDate today) {
        List<Integer> unlocked = new ArrayList<>();
        int next = 0;
        for (int d : unlockDays(1000)) {
            if (on(day1, d).isAfter(today)) {
                next = d;
                break;
            }
            unlocked.add(d);
        }
        Integer due = null;
        if (!unlocked.isEmpty()) {
            int latest = unlocked.getLast();
            boolean inWindow = today.isBefore(on(day1, latest).plusDays(DUE_FOR_DAYS));
            if (inWindow && !recorded.contains(latest)) due = latest;
        }
        return new Status(day1, List.copyOf(unlocked), due, next, on(day1, next));
    }
}
