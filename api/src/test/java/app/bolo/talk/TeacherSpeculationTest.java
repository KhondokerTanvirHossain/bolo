package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Review I4: the teacher check starts with the speculative reply (at the first pause), so most of its time is hidden
 * before the turn ends. Here the check takes about 0.9 s, the turn ends 0.9 s after speculation and the commit waits at
 * most 0.5 s: only a check started at speculation can teach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=1000", "bolo.talk.unfinished-end-after-ms=1200",
        "bolo.talk.teacher.timeout-ms=500"})
class TeacherSpeculationTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeLlm llm;

    @BeforeEach
    void setUp() {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        llm.teachReply(TeacherTalkTest.TEACH);
        llm.teachDelayMs(40);
    }

    @AfterEach
    void reset() {
        llm.teachReply(null);
        llm.teachDelayMs(0);
    }

    @Test
    void theCheckStartedAtSpeculationTeaches() throws Exception {
        TalkTestClient client = new TalkTestClient(port);
        String cookie = client.consentedGuest(5);
        client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"teacher\"}");
        String id = TalkTestClient.id(client.startTalk(cookie));
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
            s.json("{\"type\":\"played\",\"turn\":0}");
            s.await(2000, "\"your_turn\"");
            s.tone(400);
            s.zeros(1300);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"played\",\"turn\":2}");
            s.await(2000, "\"your_turn\"");
            s.tone(400);
            s.zeros(200);
            Thread.sleep(900); // the learner pauses in real time: speculation (and the early check) start here
            s.zeros(1100);
            s.await(5000, "\"type\":\"teach\"");
        }
    }
}
