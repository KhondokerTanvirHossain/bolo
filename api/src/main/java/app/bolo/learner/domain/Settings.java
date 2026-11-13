package app.bolo.learner.domain;

/** SET-1/SET-2: how Bolo sounds and whether its words show. {@code buddySpeed} is "normal" or "slower". */
public record Settings(String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent, String mood) {

    /** P3c: today's Bolo (British, female, friendly) until the learner chooses. */
    public static final Settings DEFAULTS = new Settings("normal", true, "chat", "female", "british", "friendly");

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

    public static boolean validSpeed(String speed) {
        return "normal".equals(speed) || "slower".equals(speed);
    }
}
