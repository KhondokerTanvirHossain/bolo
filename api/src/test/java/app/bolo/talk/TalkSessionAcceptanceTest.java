package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.domain.EndReason;
import app.bolo.common.id.TalkSessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** POST /api/talk and the /ws/talk door: consent first, one live talk on the free tier, only your own session. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake"})
class TalkSessionAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired StartTalkUseCase talks;

    TalkTestClient client;

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
    }

    @Test
    void startsATalkForAConsentedLearner() throws Exception {
        String cookie = client.consentedGuest(10);
        TalkTestClient.Reply talk = client.startTalk(cookie);
        assertThat(talk.status()).isEqualTo(201);
        assertThat((Integer) com.jayway.jsonpath.JsonPath.read(talk.body(), "$.minutes")).isEqualTo(10);
        Integer rows = jdbc.sql("select count(*) from talk_session where id = ?")
                .param(UUID.fromString(TalkTestClient.id(talk))).query(Integer.class).single();
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void refusesATalkWithoutConsent() throws Exception {
        TalkTestClient.Reply talk = client.startTalk(client.guest());
        assertThat(talk.status()).isEqualTo(422);
        assertThat(talk.body()).contains("\"no-consent\"");
    }

    @Test
    void refusesASecondLiveTalkWhenTheCapIsReached() throws Exception {
        TalkTestClient.Reply first = client.startTalk(client.consentedGuest(5));
        assertThat(first.status()).isEqualTo(201);
        String second = client.consentedGuest(5);
        TalkTestClient.Reply refused = client.startTalk(second);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body()).contains("\"busy\"");

        talks.end(new TalkSessionId(UUID.fromString(TalkTestClient.id(first))), EndReason.LEARNER_ENDED);
        assertThat(client.startTalk(second).status()).isEqualTo(201);
    }

    @Test
    void handshakeNeedsCookieOriginAndOwnSession() throws Exception {
        String mine = client.consentedGuest(5);
        String id = TalkTestClient.id(client.startTalk(mine));
        String other = client.consentedGuest(5);

        assertRefused(id, null, TalkTestClient.ORIGIN);
        assertRefused(id, mine, "https://evil.example");
        assertRefused(id, other, TalkTestClient.ORIGIN);

        WebSocketSession ok = connect(id, mine, TalkTestClient.ORIGIN);
        assertThat(ok.isOpen()).isTrue();
        ok.close();

        talks.end(new TalkSessionId(UUID.fromString(id)), EndReason.LEARNER_ENDED);
        assertRefused(id, mine, TalkTestClient.ORIGIN);
    }

    @Test
    void postTalkIsWriteGuarded() throws Exception {
        String cookie = client.consentedGuest(5);
        var http = java.net.http.HttpClient.newHttpClient();
        var res = http.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/talk"))
                        .header("Origin", TalkTestClient.ORIGIN).header("Cookie", cookie)
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(403);
    }

    WebSocketSession connect(String id, String cookie, String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        if (cookie != null) headers.add("Cookie", cookie);
        return new StandardWebSocketClient()
                .execute(new AbstractWebSocketHandler() { }, headers, URI.create("ws://localhost:" + port + "/ws/talk/" + id))
                .get(5, TimeUnit.SECONDS);
    }

    void assertRefused(String id, String cookie, String origin) {
        assertThatThrownBy(() -> connect(id, cookie, origin)).hasStackTraceContaining("403");
    }
}
