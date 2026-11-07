package app.bolo.talk.adapter.in.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Vendor threads hand audio to the socket; a slow network must never hold them (found live: Azure's TTS thread blocked 20 s). */
class QueuedOutputTest {

    final List<WebSocketMessage<?>> sent = new CopyOnWriteArrayList<>();
    final AtomicReference<CloseStatus> closed = new AtomicReference<>();
    final CountDownLatch network = new CountDownLatch(1);

    WebSocketSession session(boolean slow) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.isOpen()).thenAnswer(i -> closed.get() == null);
        doAnswer(i -> {
            if (slow) network.await(10, TimeUnit.SECONDS);
            sent.add(i.getArgument(0));
            return null;
        }).when(s).sendMessage(any());
        doAnswer(i -> {
            closed.set(i.getArgument(0));
            network.countDown();
            return null;
        }).when(s).close(any(CloseStatus.class));
        return s;
    }

    @Test
    void aSlowNetworkNeverBlocksTheCaller() throws Exception {
        QueuedOutput out = new QueuedOutput(session(true), JsonMapper.builder().build(), 4 * 1024 * 1024);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) out.audio(new byte[4800]);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(200);
        network.countDown();
    }

    @Test
    void messagesLeaveInOrder() throws Exception {
        QueuedOutput out = new QueuedOutput(session(false), JsonMapper.builder().build(), 4 * 1024 * 1024);
        out.send(Map.of("type", "caption"));
        out.audio(new byte[10]);
        out.send(Map.of("type", "buddy_done"));
        long until = System.currentTimeMillis() + 2000;
        while (sent.size() < 3 && System.currentTimeMillis() < until) Thread.sleep(10);
        assertThat(sent.get(0)).isInstanceOf(TextMessage.class);
        assertThat(sent.get(1)).isInstanceOf(BinaryMessage.class);
        assertThat(((TextMessage) sent.get(2)).getPayload()).contains("buddy_done");
    }

    @Test
    void aClientThatFallsTooFarBehindIsClosed() throws Exception {
        QueuedOutput out = new QueuedOutput(session(true), JsonMapper.builder().build(), 100_000);
        for (int i = 0; i < 40; i++) out.audio(new byte[4800]);
        assertThat(closed.get()).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    void closeIsSentAfterWhatIsQueued() throws Exception {
        QueuedOutput out = new QueuedOutput(session(false), JsonMapper.builder().build(), 4 * 1024 * 1024);
        out.send(Map.of("type", "ended"));
        out.close(false);
        long until = System.currentTimeMillis() + 2000;
        while (closed.get() == null && System.currentTimeMillis() < until) Thread.sleep(10);
        assertThat(sent).hasSize(1);
        assertThat(closed.get()).isEqualTo(CloseStatus.NORMAL);
    }
}
