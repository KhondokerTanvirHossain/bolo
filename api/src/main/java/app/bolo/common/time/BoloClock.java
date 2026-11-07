package app.bolo.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The one source of "now". Bolo's calendar (free-talk resets, reminders, streaks) runs on Bangladesh time. */
public class BoloClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private final Clock clock;

    public BoloClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }
}
