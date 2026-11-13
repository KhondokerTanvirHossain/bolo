package app.bolo.talk.adapter.in.ws;

import app.bolo.common.id.LearnerId;
import app.bolo.talk.application.port.in.ClientMessage;
import app.bolo.talk.application.port.in.TalkConnection;
import app.bolo.talk.application.port.in.TalkRelayUseCase;
import app.bolo.talk.application.port.in.TalkView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;

/** /ws/talk/{id}: binary frames are the learner's audio, text frames are small JSON control messages. */
@Slf4j
class TalkSocketHandler extends AbstractWebSocketHandler {

    private static final String CONNECTION = "talk.connection";
    /** About 40 s of Bolo's 24 kHz audio waiting on a slow network before the socket is given up. */
    static final long MAX_QUEUED_BYTES = 2 * 1024 * 1024;

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
        TalkView talk = (TalkView) session.getAttributes().get(TalkHandshakeInterceptor.TALK);
        LearnerId learner = (LearnerId) session.getAttributes().get(TalkHandshakeInterceptor.LEARNER);
        session.getAttributes().put(CONNECTION, relay.open(talk, learner, new QueuedOutput(session, json, MAX_QUEUED_BYTES)));
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
        // Diagnosis (2026-11-14, talks closing seconds in on a phone): how each talk socket closed.
        log.info("talk socket {} closed: {}", session.getId(), status.getCode());
        TalkConnection c = (TalkConnection) session.getAttributes().get(CONNECTION);
        if (c != null) c.onClosed();
    }

    private static TalkConnection connection(WebSocketSession session) {
        return (TalkConnection) session.getAttributes().get(CONNECTION);
    }
}
