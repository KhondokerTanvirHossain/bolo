package app.bolo.talk;

import app.bolo.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Review #2: a socket that stays open but sends no audio (a half-open phone connection) is ended by the server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.no-audio-timeout-ms=1000"})
class TalkWatchdogAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;

    @Test
    void aClientThatSendsNoAudioIsEnded() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        TalkTestClient client = new TalkTestClient(port);
        String cookie = client.consentedGuest(5);
        String id = TalkTestClient.id(client.startTalk(cookie));
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(4000, "\"ended\"", "\"disconnected\"");
        }
        assertThat(jdbc.sql("select end_reason from talk_session where id = ?").param(UUID.fromString(id))
                .query(String.class).single()).isEqualTo("disconnected");
    }
}
