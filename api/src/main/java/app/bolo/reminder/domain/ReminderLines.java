package app.bolo.reminder.domain;

import app.bolo.reminder.domain.ReminderRules.Kind;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** What a reminder says (spec 2026-11-14, "What a reminder says"). */
public final class ReminderLines {

    public static final String PRIVATE = "Time for today's talk.";
    public static final List<String> PLAIN = List.of(PRIVATE, "Bolo has a question for you today.", "Bolo is on the hilltop. Got 5 minutes?");
    public static final String TALKS_BACK = "Your 2 free talks are back.";
    public static final String COMEBACK = "We're here whenever you're ready.";
    static final int MAX = 80;
    private static final Pattern BANNED = Pattern.compile("\\b(english|course|courses|lesson|lessons|practi[cs]e|practi[cs]ing|streak|missed)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> SLOTS = Map.of("08:00", "morning", "13:30", "lunch", "18:30", "evening", "22:00", "night");

    private ReminderLines() {
    }

    /** {@code storyLine}: the stored line, already checked fresh by the caller (null when none). */
    public static String text(Kind kind, boolean keepPrivate, String storyLine, int rotation) {
        if (kind == Kind.COMEBACK) return COMEBACK;
        if (kind == Kind.TALKS_BACK) return TALKS_BACK;
        if (keepPrivate) return PRIVATE;
        if (valid(storyLine)) return storyLine;
        return PLAIN.get(Math.floorMod(rotation, PLAIN.size()));
    }

    /** One short line, no emoji, never English, lessons, practice, streaks or missed days. */
    public static boolean valid(String line) {
        if (line == null || line.isBlank() || line.length() > MAX || line.contains("\n") || line.contains("\r")) return false;
        if (line.codePoints().anyMatch(ReminderLines::emoji)) return false;
        return !BANNED.matcher(line).find();
    }

    private static boolean emoji(int cp) {
        int type = Character.getType(cp);
        return Character.isSupplementaryCodePoint(cp) || type == Character.OTHER_SYMBOL || (cp >= 0x2600 && cp <= 0x27BF);
    }

    /** For reminder_sent: the slot a time belongs to, or "exact". */
    public static String slot(String localTime) {
        return SLOTS.getOrDefault(localTime, "exact");
    }
}
