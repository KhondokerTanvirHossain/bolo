package app.bolo.speech.application.port.in;

/** The vendor is at its concurrency limit (Azure F0 allows one live recognition); try again in a few seconds. */
public class SpeechBusyException extends RuntimeException {

    public SpeechBusyException() {
        super("speech vendor busy");
    }
}
