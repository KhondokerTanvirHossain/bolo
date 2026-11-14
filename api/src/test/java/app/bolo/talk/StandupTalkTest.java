package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/** SCN-2, US-7.3, US-7.4 (2026-11-15): a Standup rehearsal, where Bolo is the learner's manager. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class StandupTalkTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeLlm llm;

    TalkTestClient client;
    String cookie;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
    }

    @AfterEach
    void reset() {
        stt.nextText(null);
    }

    void settings(String fields) throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true" + fields + "}").status())
                .isEqualTo(204);
    }

    TalkSocket yourTurn() throws Exception {
        String id = TalkTestClient.id(client.startTalk(cookie));
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        s.json("{\"type\":\"played\",\"turn\":0}");
        s.await(2000, "\"your_turn\"");
        return s;
    }

    @Test
    void aStandupTalkOpensWithTheManagerPrompt() throws Exception {
        settings(",\"scenario\":\"standup\"");
        try (TalkSocket s = yourTurn()) {
            assertThat(llm.lastReplyRequest()).contains("manager").contains("blockers");
        }
    }

    @Test
    void aFreeTalkHasNoManager() throws Exception {
        settings(",\"scenario\":\"free\"");
        try (TalkSocket s = yourTurn()) {
            assertThat(llm.lastReplyRequest()).doesNotContain("manager");
        }
    }
}
