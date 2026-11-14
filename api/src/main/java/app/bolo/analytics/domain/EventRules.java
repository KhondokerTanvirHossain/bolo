package app.bolo.analytics.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which events the browser may send and what they may carry (PRD §10: no audio, no transcript text). Props are
 * numbers, booleans or short lower-case keys, so free text can't be smuggled in.
 */
public final class EventRules {

    public static final Set<String> CLIENT = Set.of("app_first_open", "mic_permission_result", "onboarding_step_completed",
            "first_word_spoken", "debrief_viewed", "talk_client_error");
    public static final Set<String> SERVER = Set.of("session_started", "session_ended", "reminder_sent", "reminder_opened");
    static final int MAX_PROPS = 8;
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,40}");
    /** Single keys only (no spaces), so not even a short lower-case sentence fits. */
    private static final Pattern VALUE = Pattern.compile("[a-z0-9_-]{1,40}");
    /** Large enough for any count or duration we send; too small for a phone number. */
    static final double MAX_NUMBER = 10_000_000;

    private EventRules() {
    }

    /** Empty when the browser may send this; otherwise what's wrong (never the value itself). */
    public static Optional<String> problem(String name, Map<String, Object> props) {
        if (name == null || !CLIENT.contains(name)) return Optional.of("unknown event");
        if (props == null) return Optional.empty();
        if (props.size() > MAX_PROPS) return Optional.of("too many props");
        for (var e : props.entrySet()) {
            if (!KEY.matcher(e.getKey()).matches()) return Optional.of("bad prop name");
            Object v = e.getValue();
            boolean ok = v instanceof Boolean
                    || v instanceof Number n && Double.isFinite(n.doubleValue()) && Math.abs(n.doubleValue()) < MAX_NUMBER
                    || v instanceof String s && VALUE.matcher(s).matches();
            if (!ok) return Optional.of("bad value for " + e.getKey());
        }
        return Optional.empty();
    }
}
