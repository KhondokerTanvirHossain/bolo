package app.bolo.talk.adapter.in.ws;

import app.bolo.talk.application.port.in.StartTalkUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Configuration
@EnableWebSocket
class TalkWebSocketConfig implements WebSocketConfigurer {

    private final StartTalkUseCase talks;
    private final String publicOrigin;

    TalkWebSocketConfig(StartTalkUseCase talks, @Value("${bolo.public-origin}") String publicOrigin) {
        this.talks = talks;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AbstractWebSocketHandler() {
                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        session.close(CloseStatus.NOT_ACCEPTABLE);
                    }
                }, "/ws/talk/*")
                .addInterceptors(new TalkHandshakeInterceptor(talks, publicOrigin))
                .setAllowedOrigins(publicOrigin);
    }
}
