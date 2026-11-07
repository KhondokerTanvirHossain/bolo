package app.bolo.talk;

import app.bolo.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** TLK-9: at the chosen talk time Bolo wraps up after the learner's next turn, then the talk ends. A "minute" is 200 ms here. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake", "bolo.talk.minute-ms=200", "bolo.talk.wrap-grace-ms=30000",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class TalkTimeLimitAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;

    @Test
    void timeLimitWrapsUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        TalkTestClient client = new TalkTestClient(port);
        String cookie = client.consentedGuest(5);
        String id = TalkTestClient.id(client.startTalk(cookie));
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
            Thread.sleep(1100);
            s.json("{\"type\":\"played\",\"turn\":0}");
            s.await(2000, "\"your_turn\"");
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"played\",\"turn\":2}");
            s.await(2000, "\"ended\"", "\"time_up\"");
        }
    }
}
