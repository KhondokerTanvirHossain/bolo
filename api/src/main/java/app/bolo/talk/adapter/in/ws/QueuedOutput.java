package app.bolo.talk.adapter.in.ws;

import app.bolo.talk.application.port.in.TalkOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything bound for the browser goes through one queue, written in order by one thread. Callers - the relay,
 * Azure's synthesis callback thread, the LLM stream - only enqueue, so a slow network can never hold a vendor thread
 * or the conversation (found live: a blocking socket write stalled Azure's TTS callback for 20 s and the turn never
 * finished). A browser that falls more than {@code maxQueuedBytes} of audio behind is closed as unreliable.
 */
@Slf4j
final class QueuedOutput implements TalkOutput {

    private record Close(CloseStatus status) {
    }

    private final WebSocketSession session;
    private final JsonMapper json;
    private final long maxQueuedBytes;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Thread writer;

    QueuedOutput(WebSocketSession session, JsonMapper json, long maxQueuedBytes) {
        this.session = session;
        this.json = json;
        this.maxQueuedBytes = maxQueuedBytes;
        this.writer = Thread.ofVirtual().name("talk-writer").start(this::drain);
    }

    boolean writerAlive() {
        return writer.isAlive();
    }

    @Override
    public void send(Map<String, Object> message) {
        if (closing.get()) return;
        queue.add(new TextMessage(json.writeValueAsString(message)));
    }

    @Override
    public void audio(byte[] pcm24k) {
        if (closing.get()) return;
        if (queuedBytes.addAndGet(pcm24k.length) > maxQueuedBytes) {
            log.warn("talk socket {} fell too far behind; closing", session.getId());
            closeNow(CloseStatus.SESSION_NOT_RELIABLE);
            return;
        }
        queue.add(new BinaryMessage(pcm24k));
    }

    @Override
    public void close(boolean error) {
        if (closing.compareAndSet(false, true)) queue.add(new Close(error ? CloseStatus.SERVER_ERROR : CloseStatus.NORMAL));
    }

    private void closeNow(CloseStatus status) {
        if (!closing.compareAndSet(false, true)) return;
        queue.clear();
        queue.add(new Close(status));
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("talk close: {}", e.getClass().getSimpleName());
        }
    }

    private void drain() {
        try {
            while (true) {
                Object item = queue.take();
                if (item instanceof Close c) {
                    if (session.isOpen()) session.close(c.status());
                    return;
                }
                WebSocketMessage<?> m = (WebSocketMessage<?>) item;
                if (!session.isOpen()) return;
                session.sendMessage(m);
                if (m instanceof BinaryMessage b) queuedBytes.addAndGet(-b.getPayloadLength());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("talk socket {} write failed: {}", session.getId(), e.getClass().getSimpleName());
            closeNow(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }
}
