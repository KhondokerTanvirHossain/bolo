package app.bolo.talk.adapter.in.ws;

import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkRelayUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.standard.StandardWebSocketUpgradeStrategy;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocket
class TalkWebSocketConfig implements WebSocketConfigurer {

    private final StartTalkUseCase talks;
    private final TalkRelayUseCase relay;
    private final JsonMapper json;
    private final String publicOrigin;

    TalkWebSocketConfig(StartTalkUseCase talks, TalkRelayUseCase relay, JsonMapper json,
                        @Value("${bolo.public-origin}") String publicOrigin) {
        this.talks = talks;
        this.relay = relay;
        this.json = json;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new TalkSocketHandler(relay, json), "/ws/talk/*")
                .setHandshakeHandler(new NoCompressionHandshake())
                .addInterceptors(new TalkHandshakeInterceptor(talks, publicOrigin))
                .setAllowedOrigins(publicOrigin);
    }

    /**
     * The talk socket never agrees to permessage-deflate (2026-11-14): Chrome on an iPhone (WebKit) closed talks
     * seconds in with 1002 protocol error while compression was on, and PCM audio barely compresses anyway.
     * Tomcat negotiates extensions from the request header itself (Spring's extension filter doesn't reach it),
     * so the offer is hidden from Tomcat.
     */
    static final class NoCompressionHandshake extends DefaultHandshakeHandler {
        NoCompressionHandshake() {
            super(new StandardWebSocketUpgradeStrategy() {
                @Override
                public void upgrade(ServerHttpRequest request, ServerHttpResponse response, String protocol,
                                    List<WebSocketExtension> extensions, Principal user, WebSocketHandler handler,
                                    Map<String, Object> attributes) throws HandshakeFailureException {
                    HttpServletRequest raw = ((ServletServerHttpRequest) request).getServletRequest();
                    super.upgrade(new ServletServerHttpRequest(new WithoutExtensions(raw)), response, protocol,
                            List.of(), user, handler, attributes);
                }
            });
        }

        @Override
        protected List<WebSocketExtension> filterRequestedExtensions(ServerHttpRequest request,
                List<WebSocketExtension> requested, List<WebSocketExtension> supported) {
            return List.of();
        }
    }

    private static final class WithoutExtensions extends HttpServletRequestWrapper {
        private static final String HEADER = "Sec-WebSocket-Extensions";

        WithoutExtensions(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(n -> !HEADER.equalsIgnoreCase(n)).toList());
        }
    }
}
