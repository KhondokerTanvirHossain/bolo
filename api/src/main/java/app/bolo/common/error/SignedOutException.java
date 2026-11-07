package app.bolo.common.error;

/** No valid session on a request that needs one. */
public class SignedOutException extends RuntimeException {

    public SignedOutException() {
        super("signed-out");
    }
}
