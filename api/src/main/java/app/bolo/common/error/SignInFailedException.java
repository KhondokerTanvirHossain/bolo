package app.bolo.common.error;

/** A Google sign-in that couldn't be verified (ACC-1). */
public class SignInFailedException extends RuntimeException {
    public SignInFailedException() {
        super("sign-in-failed");
    }
}
