package app.bolo.talk.adapter.in.ws;

import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkRelayUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.json.JsonMapper;

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
                .addInterceptors(new TalkHandshakeInterceptor(talks, publicOrigin))
                .setAllowedOrigins(publicOrigin);
    }
}
