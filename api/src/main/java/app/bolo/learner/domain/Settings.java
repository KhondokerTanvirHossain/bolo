package app.bolo.learner.domain;

/** SET-1/SET-2: how Bolo sounds and whether its words show. {@code buddySpeed} is "normal" or "slower". */
public record Settings(String buddySpeed, boolean captions) {

    public static final Settings DEFAULTS = new Settings("normal", true);

    public static boolean validSpeed(String speed) {
        return "normal".equals(speed) || "slower".equals(speed);
    }
}
