package app.bolo.speech.application.port.in;

/** The speech vendor failed or timed out. Callers end the talk kindly; there is no retry mid-turn. */
public class SpeechUnavailableException extends RuntimeException {

    public SpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpeechUnavailableException(String message) {
        super(message);
    }
}
