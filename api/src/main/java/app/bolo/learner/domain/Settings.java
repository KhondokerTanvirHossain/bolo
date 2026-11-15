package app.bolo.learner.domain;

/** SET-1/SET-2: how Bolo sounds and whether its words show. {@code buddySpeed} is "normal" or "slower". */
/** AX-7, AX-8 (2026-11-14): {@code answerTime} the learner's choice ("auto" by default), {@code answerTimeAuto} Bolo's pick
 * from their start delays (only Bolo sets it), {@code stammer} the I stammer switch. */
public record Settings(String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent, String mood,
                       String answerTime, String answerTimeAuto, boolean stammer, String scenario, String difficulty,
                       Integer talkMinutes) {

    /** P3c: today's Bolo (British, female, friendly) until the learner chooses. */
    public static final Settings DEFAULTS = new Settings("normal", true, "chat", "female", "british", "friendly", "auto", "normal", false, "free", "realistic", null);

    /** P3c (SET-5): "female" or "male". */
    public static boolean validGender(String g) {
        return "female".equals(g) || "male".equals(g);
    }

    /** P3c (SET-5): "british", "american" or "indian". */
    public static boolean validAccent(String a) {
        return "british".equals(a) || "american".equals(a) || "indian".equals(a);
    }

    /** P3c (SET-6): "friendly", "funny", "professional" or "calm". */
    public static boolean validMood(String m) {
        return "friendly".equals(m) || "funny".equals(m) || "professional".equals(m) || "calm".equals(m);
    }

    /** P3a: "chat" (the live chat, the default) or "classic" (Bolo's caption only). */
    public static boolean validTalkView(String view) {
        return "chat".equals(view) || "classic".equals(view);
    }

    /** AX-7: "auto", "quick", "normal" or "relaxed". */
    public static boolean validAnswerTime(String t) {
        return "auto".equals(t) || validAutoSpeed(t);
    }

    /** AX-7: the speeds Bolo can pick: "quick", "normal" or "relaxed". */
    public static boolean validAutoSpeed(String t) {
        return "quick".equals(t) || "normal".equals(t) || "relaxed".equals(t);
    }

    /** SCN-1 (2026-11-15) and hats (2026-11-16): "free", "standup", "teacher" or "ielts". */
    public static boolean validScenario(String s) {
        return "free".equals(s) || "standup".equals(s) || "teacher".equals(s) || "ielts".equals(s);
    }

    /** 2026-11-16: a talk is 5 or 10 minutes (10 only on Premium and Ultimate, decided when the talk starts). */
    public static boolean validTalkMinutes(Integer m) {
        return m != null && (m == 5 || m == 10);
    }

    /** SCN-2: a standup's "friendly" (never interrupts) or "realistic" (cuts in once or twice). */
    public static boolean validDifficulty(String d) {
        return "friendly".equals(d) || "realistic".equals(d);
    }

    public static boolean validSpeed(String speed) {
        return "normal".equals(speed) || "slower".equals(speed);
    }
}
