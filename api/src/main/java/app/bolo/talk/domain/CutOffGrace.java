package app.bolo.talk.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TLK-10 changed 2026-11-14 (founder): instead of resuming a talk, a talk that was cut off (the connection closed, or
 * Bolo's voice failed) doesn't use up a talk when the learner starts again within 30 minutes. While those 30 minutes
 * run it doesn't count either, so a learner whose last talk was cut off can always start again. At most 2 cut-off talks
 * a Bangladesh day are forgiven (the bill); later ones count.
 */
public final class CutOffGrace {

    public static final Duration WITHIN = Duration.ofMinutes(30);
    public static final int PER_DAY = 2;
    static final Set<String> CUT_OFF = Set.of(EndReason.DISCONNECTED.dbValue(), EndReason.VOICE_UNAVAILABLE.dbValue());

    /**
     * One of the learner's talks. ended and endReason are null while it's open; counted is the plan's counting rule
     * before this grace (see {@code TalkSessionPort#talks}).
     */
    public record Talk(Instant started, Instant ended, String endReason, boolean counted) {
        boolean cutOff() {
            return ended != null && CUT_OFF.contains(endReason);
        }
    }

    private CutOffGrace() {
    }

    /** When each talk that uses up a talk started. */
    public static List<Instant> countedStarts(List<Talk> talks, Instant now) {
        List<Talk> ordered = talks.stream().sorted(Comparator.comparing(Talk::started)).toList();
        Map<LocalDate, Integer> forgivenOn = new HashMap<>();
        return ordered.stream().filter(Talk::counted).filter(t -> {
            if (!t.cutOff() || !cameBack(t, ordered, now)) return true;
            LocalDate day = t.started().atZone(FreePlan.ZONE).toLocalDate();
            int forgiven = forgivenOn.getOrDefault(day, 0);
            if (forgiven >= PER_DAY) return true;
            forgivenOn.put(day, forgiven + 1);
            return false;
        }).map(Talk::started).toList();
    }

    /** Still within the 30 minutes, or another talk started within them. */
    private static boolean cameBack(Talk cut, List<Talk> talks, Instant now) {
        Instant until = cut.ended().plus(WITHIN);
        if (now.isBefore(until)) return true;
        return talks.stream().anyMatch(t -> t != cut && !t.started().isBefore(cut.ended()) && !t.started().isAfter(until));
    }
}
