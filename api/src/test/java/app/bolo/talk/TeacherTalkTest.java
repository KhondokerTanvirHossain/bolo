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

/** The English teacher hat (2026-11-16): Bolo shows a better way, the learner tries it, Bolo judges. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.talk.teacher.timeout-ms=500"})
class TeacherTalkTest extends PostgresTestBase {

    static final String BETTER = "Hello Bolo, it is nice to talk with you.";
    static final String TEACH = "{\"teach\":true,\"better\":\"" + BETTER + "\",\"why\":\"It sounds friendlier.\"}";

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
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"teacher\"}")
                .status()).isEqualTo(204);
        llm.teachReply(TEACH);
    }

    @AfterEach
    void reset() {
        stt.nextText(null);
        llm.teachReply(null);
        llm.teachDelayMs(0);
    }

    TalkSocket open() throws Exception {
        String id = TalkTestClient.id(client.startTalk(cookie));
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        return s;
    }

    /** The learner's turn after Bolo's turn {@code buddy}; returns when Bolo's next turn is done. */
    void say(TalkSocket s, int buddy) throws Exception {
        speak(s, buddy);
        s.await(4000, "\"buddy_done\"", "\"turn\":" + (buddy + 2));
    }

    /** The learner's turn after Bolo's turn {@code buddy}, without waiting for Bolo's answer. */
    void speak(TalkSocket s, int buddy) throws Exception {
        s.json("{\"type\":\"played\",\"turn\":" + buddy + "}");
        s.await(2000, "\"your_turn\"");
        s.tone(400);
        s.zeros(600);
    }

    int events(String name) {
        return jdbc.sql("select count(*) from event where name = ? and learner_id = (select learner_id from talk_session order by started_at desc limit 1)")
                .param(name).query(Integer.class).single();
    }

    @Test
    void theFirstTurnIsNeverTaught() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            assertThat(s.quiet(100, "\"type\":\"teach\"")).isTrue();
        }
    }

    @Test
    void theSecondTurnIsTaughtAndTheRetryJudged() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            speak(s, 2);
            s.await(4000, "\"type\":\"teach\"", BETTER);
            s.await(4000, "\"buddy_done\"", "\"turn\":4");
            stt.nextText(BETTER);
            speak(s, 4);
            s.await(4000, "\"type\":\"judged\"", "\"result\":\"nailed\"");
            s.await(4000, "\"buddy_done\"", "\"turn\":6");
            assertThat(llm.lastReplyRequest()).contains("Start your reply with exactly \"Nailed it!\"");
        }
        assertThat(events("teacher_taught")).isEqualTo(1);
        assertThat(events("teacher_judged")).isEqualTo(1);
    }

    @Test
    void aTeachLineIsSpokenWithoutTheModel() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            speak(s, 2);
            s.await(4000, "A smoother way to say that");
        }
    }

    /** Review Focus 1: a slow or failing check means an ordinary reply. */
    @Test
    void aSlowCheckMeansAnOrdinaryReply() throws Exception {
        llm.teachDelayMs(2000);
        try (TalkSocket s = open()) {
            say(s, 0);
            speak(s, 2);
            s.await(4000, "\"caption\"", "That sounds great");
        }
        assertThat(events("teacher_taught")).isZero();
    }

    /** Review Focus 2: "Sorry?" on the retry is answered as TLK-13; the retry is dropped, never judged later. */
    @Test
    void aSorryRetryIsNotJudged() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            say(s, 2);
            stt.nextText("Sorry?");
            say(s, 4);
            stt.nextText(BETTER);
            say(s, 6);
            assertThat(s.quiet(100, "\"type\":\"judged\"")).isTrue();
        }
        assertThat(events("teacher_judged")).isZero();
    }

    @Test
    void aFreeTalkIsNeverTaught() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"free\"}")
                .status()).isEqualTo(204);
        try (TalkSocket s = open()) {
            say(s, 0);
            say(s, 2);
            assertThat(s.quiet(100, "\"type\":\"teach\"")).isTrue();
        }
    }
}
