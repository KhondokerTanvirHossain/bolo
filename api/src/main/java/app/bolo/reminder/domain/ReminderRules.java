package app.bolo.reminder.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * When a daily reminder goes out (PRG-5) and when Bolo backs off (PRG-6). Pure: the caller persists a decision only when
 * it sends (or stops), so deciding twice from the same state gives the same answer.
 */
public final class ReminderRules {

    /** A reminder may go out from the chosen time until 2 hours later, never later (a late job sends nothing). */
    static final Duration WINDOW = Duration.ofHours(2);
    static final int STOP_AFTER_IGNORED = 3;
    static final int COMEBACK_AFTER_DAYS = 7;

    /** The learner's reminder row as the rules need it; {@code lastSentOn} is a local day. */
    public record State(boolean on, LocalTime time, ZoneId zone, LocalDate lastSentOn, Instant lastSentAt, boolean tapped,
                        int ignored, Instant stoppedAt, boolean comebackSent) {
    }

    /**
     * talkedToday: a talk started on the window's local day; talkSinceLastSent: a talk started after the last reminder;
     * talkAvailable: the learner can start a talk today; talksBackToday: free talks came back today (free plan).
     */
    public record Facts(boolean talkedToday, boolean talkSinceLastSent, boolean talkAvailable, boolean talksBackToday) {
    }

    public enum Kind { NONE, NORMAL, TALKS_BACK, COMEBACK }

    /** day: the local day the reminder belongs to; ignored: the run of ignored reminders before this one; stop: PRG-6. */
    public record Decision(Kind kind, LocalDate day, int ignored, boolean stop) {
        static Decision none(int ignored) {
            return new Decision(Kind.NONE, null, ignored, false);
        }
    }

    private ReminderRules() {
    }

    /** The local day whose window [time, time + 2 h) contains {@code now}; a window crossing midnight is the earlier day. */
    public static Optional<LocalDate> windowDay(State s, Instant now) {
        LocalDate today = now.atZone(s.zone()).toLocalDate();
        for (LocalDate day : new LocalDate[]{today, today.minusDays(1)}) {
            ZonedDateTime start = day.atTime(s.time()).atZone(s.zone());
            Instant from = start.toInstant();
            if (!now.isBefore(from) && now.isBefore(from.plus(WINDOW))) return Optional.of(day);
        }
        return Optional.empty();
    }

    public static Decision decide(State s, Facts f, Instant now) {
        if (!s.on()) return Decision.none(s.ignored());
        Optional<LocalDate> window = windowDay(s, now);
        if (window.isEmpty()) return Decision.none(s.ignored());
        LocalDate day = window.get();
        if (day.equals(s.lastSentOn())) return Decision.none(s.ignored());

        if (s.stoppedAt() != null) {
            LocalDate stoppedOn = s.stoppedAt().atZone(s.zone()).toLocalDate();
            if (s.comebackSent() || day.isBefore(stoppedOn.plusDays(COMEBACK_AFTER_DAYS))) return Decision.none(s.ignored());
            return new Decision(Kind.COMEBACK, day, s.ignored(), false);
        }

        // The last reminder's outcome is final once the next window opens: tapped or followed by a talk resets the run.
        boolean lastIgnored = s.lastSentAt() != null && !s.tapped() && !f.talkSinceLastSent();
        int ignored = s.lastSentAt() == null ? s.ignored() : lastIgnored ? s.ignored() + 1 : 0;
        if (ignored >= STOP_AFTER_IGNORED) return new Decision(Kind.NONE, day, ignored, true);

        if (f.talkedToday() || !f.talkAvailable()) return Decision.none(ignored);
        return new Decision(f.talksBackToday() ? Kind.TALKS_BACK : Kind.NORMAL, day, ignored, false);
    }
}
