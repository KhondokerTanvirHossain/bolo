package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Review I1: a turn that starts outside the talk's last 30 s but runs into them is not cut in on. Talk = 50 s here. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake", "bolo.talk.minute-ms=10000",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.talk.standup.cut-in-after-ms=600", "bolo.talk.standup.cut-in-silence-ms=150", "bolo.talk.standup.cut-in-not-last-ms=46000"})
class StandupCutInLateTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeLlm llm;

    @BeforeEach
    void setUp() {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
    }

    @Test
    void noCutInOnceTheLast30sBegin() throws Exception {
        TalkTestClient client = new TalkTestClient(port);
        String cookie = client.consentedGuest(5);
        assertThat(client.send("PUT", "/api/me/settings", cookie,
                "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"standup\",\"difficulty\":\"realistic\"}").status()).isEqualTo(204);
        String id = TalkTestClient.id(client.startTalk(cookie));
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
            s.json("{\"type\":\"played\",\"turn\":0}");
            s.await(2000, "\"your_turn\"");
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"played\",\"turn\":2}");
            String turn = s.await(2000, "\"your_turn\"");
            assertThat(turn).as("armed: more than 46 s left when the turn starts (about 1 s in)").isNotNull();
            Thread.sleep(5_000);
            s.tone(800);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":4");
            assertThat(llm.lastReplyRequest()).doesNotContain("cutting in");
        }
    }
}
