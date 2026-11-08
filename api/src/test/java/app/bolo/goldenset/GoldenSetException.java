package app.bolo.goldenset;

/** A golden set that can't be used as given; the message names the recording or file and what to fix. */
public class GoldenSetException extends RuntimeException {
    public GoldenSetException(String message) {
        super(message);
    }
}
