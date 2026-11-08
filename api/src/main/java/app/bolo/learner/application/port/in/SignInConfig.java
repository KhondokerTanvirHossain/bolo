package app.bolo.learner.application.port.in;

/** Firebase's public web config (not secret). Sign-in is on only when all four values are set. */
public record SignInConfig(String apiKey, String authDomain, String projectId, String appId) {

    public boolean enabled() {
        return !blank(apiKey) && !blank(authDomain) && !blank(projectId) && !blank(appId);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
