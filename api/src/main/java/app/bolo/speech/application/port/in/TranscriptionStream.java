package app.bolo.speech.application.port.in;

/** A live recognition fed with PCM s16le, mono, 16 kHz. */
public interface TranscriptionStream {

    void write(byte[] pcm16k);

    /** Starts a new utterance: everything heard so far is forgotten and textSoFar restarts empty. */
    void mark();

    void close();
}
