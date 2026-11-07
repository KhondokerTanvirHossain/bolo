package app.bolo.common.error;

/** A rule refused the request. {@code code} is a stable kebab-case value the web client switches on. */
public class RefusedException extends RuntimeException {

    private final String code;

    public RefusedException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
