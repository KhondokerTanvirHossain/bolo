package app.bolo.talk.adapter.in.ws;

import app.bolo.common.id.LearnerId;
import app.bolo.talk.application.port.in.ClientMessage;
import app.bolo.talk.application.port.in.TalkConnection;
import app.bolo.talk.application.port.in.TalkOutput;
import app.bolo.talk.application.port.in.TalkRelayUseCase;
import app.bolo.talk.application.port.in.TalkView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/** /ws/talk/{id}: binary frames are the learner's audio, text frames are small JSON control messages. */
@Slf4j
class TalkSocketHandler extends AbstractWebSocketHandler {

    private static final String CONNECTION = "talk.connection";

    private final TalkRelayUseCase relay;
    private final JsonMapper json;

    TalkSocketHandler(TalkRelayUseCase relay, JsonMapper json) {
        this.relay = relay;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Audio frames are 640 bytes; 64 KB leaves room for a browser that batches. Control messages are tiny.
        session.setBinaryMessageSizeLimit(64 * 1024);
        session.setTextMessageSizeLimit(8 * 1024);
        var safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 1 << 20);
        TalkView talk = (TalkView) session.getAttributes().get(TalkHandshakeInterceptor.TALK);
        LearnerId learner = (LearnerId) session.getAttributes().get(TalkHandshakeInterceptor.LEARNER);
        session.getAttributes().put(CONNECTION, relay.open(talk, learner, new Output(safe)));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer payload = message.getPayload();
        if (payload.remaining() % 2 != 0) return;
        byte[] pcm = new byte[payload.remaining()];
        payload.get(pcm);
        connection(session).onAudio(pcm);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientMessage m;
        try {
            m = json.readValue(message.getPayload(), ClientMessage.class);
        } catch (RuntimeException e) {
            return;
        }
        connection(session).onClient(m);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TalkConnection c = (TalkConnection) session.getAttributes().get(CONNECTION);
        if (c != null) c.onClosed();
    }

    private static TalkConnection connection(WebSocketSession session) {
        return (TalkConnection) session.getAttributes().get(CONNECTION);
    }

    private final class Output implements TalkOutput {

        private final WebSocketSession session;

        Output(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void send(Map<String, Object> message) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(message)));
            } catch (IOException | RuntimeException e) {
                log.debug("talk send dropped: {}", e.getClass().getSimpleName());
            }
        }

        @Override
        public void audio(byte[] pcm24k) {
            try {
                if (session.isOpen()) session.sendMessage(new BinaryMessage(pcm24k));
            } catch (IOException | RuntimeException e) {
                log.debug("talk audio dropped: {}", e.getClass().getSimpleName());
            }
        }

        @Override
        public void close(boolean error) {
            try {
                session.close(error ? CloseStatus.SERVER_ERROR : CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("talk close: {}", e.getClass().getSimpleName());
            }
        }
    }
}
