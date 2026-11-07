package app.bolo.talk.application.port.in;

import java.util.Map;

/** The relay's way back to the browser; implementations are thread-safe. */
public interface TalkOutput {

    void send(Map<String, Object> message);

    void audio(byte[] pcm24k);

    /** Closes the socket; {@code error} closes with 1011 so the client shows the "lost its voice" state. */
    void close(boolean error);
}
