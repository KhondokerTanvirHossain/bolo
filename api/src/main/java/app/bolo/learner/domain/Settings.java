package app.bolo.learner.domain;

/** SET-1/SET-2: how Bolo sounds and whether its words show. {@code buddySpeed} is "normal" or "slower". */
public record Settings(String buddySpeed, boolean captions, String talkView) {

    public static final Settings DEFAULTS = new Settings("normal", true, "chat");

    /** P3a: "chat" (the live chat, the default) or "classic" (Bolo's caption only). */
    public static boolean validTalkView(String view) {
        return "chat".equals(view) || "classic".equals(view);
    }

    public static boolean validSpeed(String speed) {
        return "normal".equals(speed) || "slower".equals(speed);
    }
}
