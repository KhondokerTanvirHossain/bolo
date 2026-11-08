package app.bolo.learner.application.port.in;

public interface SignInUseCase {

    /** {@code token}: the new session token to set as the cookie (the old one is retired). */
    record SignedIn(LearnerView learner, String token) {
    }

    /**
     * Continue with Google (ACC-1, US-8.1, US-8.4): signs into the Google user's account, turning the cookie's guest into
     * it or moving the guest's data into an existing one. Throws SignInFailedException for a token that doesn't verify.
     */
    SignedIn signIn(String currentTokenOrNull, String idToken, String inviteOrNull);

    void signOut(String tokenOrNull);
}
