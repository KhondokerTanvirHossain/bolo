package app.bolo.talk.adapter.in.ws;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkView;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * The door to /ws/talk/{id}: the session cookie (already resolved by the cookie filter), the exact public Origin
 * (a cross-site page can't open a socket with the learner's cookie), consent to the current policy (NFR-16), and
 * the learner's own live talk, attached once. Anything else gets 403 before the upgrade.
 */
class TalkHandshakeInterceptor implements HandshakeInterceptor {

    static final String LEARNER = "learnerId";
    static final String TALK = "talk";

    private final StartTalkUseCase talks;
    private final String publicOrigin;

    TalkHandshakeInterceptor(StartTalkUseCase talks, String publicOrigin) {
        this.talks = talks;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        Optional<TalkView> talk = admit(request);
        if (talk.isEmpty()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(TALK, talk.get());
        attributes.put(LEARNER, ((ServletServerHttpRequest) request).getServletRequest().getAttribute(CurrentLearner.ATTRIBUTE));
        return true;
    }

    private Optional<TalkView> admit(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servlet)) return Optional.empty();
        if (!publicOrigin.equals(request.getHeaders().getOrigin())) return Optional.empty();
        if (!(servlet.getServletRequest().getAttribute(CurrentLearner.ATTRIBUTE) instanceof LearnerId learner)) {
            return Optional.empty();
        }
        String path = request.getURI().getPath();
        TalkSessionId id;
        try {
            id = TalkSessionId.of(path.substring(path.lastIndexOf('/') + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return talks.attach(learner, id);
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
    }
}
