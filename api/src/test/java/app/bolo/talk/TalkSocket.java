package app.bolo.talk;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A test client for /ws/talk: records every message (String for JSON, byte[] for audio) in arrival order. */
public final class TalkSocket implements AutoCloseable {

    public final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    public final AtomicReference<CloseStatus> closed = new AtomicReference<>();
    private final WebSocketSession session;

    public TalkSocket(int port, String id, String cookie) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(TalkTestClient.ORIGIN);
        headers.add("Cookie", cookie);
        session = new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                inbox.add(m.getPayload());
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession s, BinaryMessage m) {
                ByteBuffer b = m.getPayload();
                byte[] copy = new byte[b.remaining()];
                b.get(copy);
                inbox.add(copy);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.set(status);
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/talk/" + id)).get(5, TimeUnit.SECONDS);
    }

    /** Waits for a JSON message containing every fragment, skipping others; returns it. */
    public String await(long ms, String... fragments) throws InterruptedException {
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) {
            Object m = inbox.poll(until - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (m instanceof String s && containsAll(s, fragments)) return s;
        }
        throw new AssertionError("no message with " + String.join(" + ", fragments) + " within " + ms + " ms");
    }

    /** Waits for a binary audio message. */
    public byte[] awaitAudio(long ms) throws InterruptedException {
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) {
            Object m = inbox.poll(until - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (m instanceof byte[] b) return b;
        }
        throw new AssertionError("no audio within " + ms + " ms");
    }

    /** True if no JSON message with all fragments arrives within ms. */
    public boolean quiet(long ms, String... fragments) throws InterruptedException {
        try {
            await(ms, fragments);
            return false;
        } catch (AssertionError e) {
            return true;
        }
    }

    public void json(String json) throws Exception {
        session.sendMessage(new TextMessage(json));
    }

    public void tone(int ms) throws Exception {
        for (int i = 0; i < ms / 20; i++) session.sendMessage(new BinaryMessage(frame(true)));
    }

    public void zeros(int ms) throws Exception {
        for (int i = 0; i < ms / 20; i++) session.sendMessage(new BinaryMessage(frame(false)));
    }

    public void abort() throws Exception {
        session.close(CloseStatus.GOING_AWAY);
    }

    @Override
    public void close() throws Exception {
        if (session.isOpen()) session.close();
    }

    static boolean containsAll(String s, String... fragments) {
        for (String f : fragments) if (!s.contains(f)) return false;
        return true;
    }

    /** 20 ms of 16 kHz PCM: a -20 dBFS 440 Hz tone, or digital silence. */
    static byte[] frame(boolean loud) {
        ByteBuffer b = ByteBuffer.allocate(640).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 320; i++) b.putShort(loud ? (short) (4634 * Math.sin(2 * Math.PI * 440 * i / 16000.0)) : 0);
        return b.array();
    }
}
