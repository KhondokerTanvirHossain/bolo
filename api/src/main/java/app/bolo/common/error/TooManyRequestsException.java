package app.bolo.common.error;

/** The caller is going too fast; answered 429 {"code":"slow-down"}. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException() {
        super("slow-down");
    }
}
