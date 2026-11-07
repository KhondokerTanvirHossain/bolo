package app.bolo.speech.application.port.in;

/** Called from the recogniser's own thread. */
public interface TranscriptListener {

    /** Every final segment since the last {@link TranscriptionStream#mark()} plus the current partial, space-joined. */
    void onPartial(String textSoFar);

    void onFailure(Exception e);
}
