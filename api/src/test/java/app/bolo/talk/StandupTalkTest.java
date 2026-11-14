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
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.talk.standup.cut-in-after-ms=600", "bolo.talk.standup.cut-in-silence-ms=150"})
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

    int interrupted() {
        return jdbc.sql("select count(*) from event where name = 'standup_interrupted' and learner_id = "
                + "(select learner_id from talk_session order by started_at desc limit 1)").query(Integer.class).single();
    }

    /** Turn 1 ends normally; returns when it's the learner's turn 3. */
    void firstTurn(TalkSocket s) throws Exception {
        s.tone(400);
        s.zeros(600);
        s.await(3000, "\"buddy_done\"", "\"turn\":2");
        s.json("{\"type\":\"played\",\"turn\":2}");
        s.await(2000, "\"your_turn\"");
    }

    @Test
    void realisticCutsInWithAFollowUp() throws Exception {
        settings(",\"scenario\":\"standup\",\"difficulty\":\"realistic\"");
        try (TalkSocket s = yourTurn()) {
            firstTurn(s);
            s.tone(800);
            s.zeros(200);
            s.await(3000, "\"buddy_done\"", "\"turn\":4");
            assertThat(llm.lastReplyRequest()).contains("cutting in");
        }
        assertThat(interrupted()).isEqualTo(1);
    }

    @Test
    void theFirstTurnIsNeverCutIn() throws Exception {
        settings(",\"scenario\":\"standup\",\"difficulty\":\"realistic\"");
        try (TalkSocket s = yourTurn()) {
            s.tone(800);
            s.zeros(200);
            assertThat(s.quiet(150, "\"turn_end\"")).isTrue();
        }
    }

    @Test
    void friendlyNeverCutsIn() throws Exception {
        settings(",\"scenario\":\"standup\",\"difficulty\":\"friendly\"");
        try (TalkSocket s = yourTurn()) {
            firstTurn(s);
            s.tone(800);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":4");
            assertThat(llm.lastReplyRequest()).doesNotContain("cutting in");
        }
        assertThat(interrupted()).isZero();
    }

    /** Review Focus 3: a cut-in "Sorry?" is answered as "Sorry?" (Bolo repeats itself, no model call). */
    @Test
    void aCutInSorryIsAnsweredAsSorry() throws Exception {
        settings(",\"scenario\":\"standup\",\"difficulty\":\"realistic\"");
        try (TalkSocket s = yourTurn()) {
            firstTurn(s);
            int calls = llm.replyCalls();
            stt.nextText("Sorry?");
            s.tone(800);
            s.zeros(200);
            s.await(3000, "\"buddy_done\"", "\"turn\":4");
            assertThat(llm.replyCalls()).isEqualTo(calls);
        }
        assertThat(interrupted()).isEqualTo(1);
    }
}
