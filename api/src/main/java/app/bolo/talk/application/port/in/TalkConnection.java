package app.bolo.talk.application.port.in;

public interface TalkConnection {

    /** PCM s16le, mono, 16 kHz. */
    void onAudio(byte[] pcm16k);

    void onClient(ClientMessage message);

    /** The socket closed (by the browser, the network or the server). */
    void onClosed();
}
