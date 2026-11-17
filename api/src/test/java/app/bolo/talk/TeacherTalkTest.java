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

    /** Founder 2026-11-18: the next teach can come right after the try (teach, try, teach). */
    @Test
    void theNextTeachComesRightAfterTheTry() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            speak(s, 2);
            s.await(4000, "\"type\":\"teach\"", BETTER);
            s.await(4000, "\"buddy_done\"", "\"turn\":4");
            stt.nextText(BETTER);
            say(s, 4);
            stt.nextText(null);
            speak(s, 6);
            s.await(4000, "\"type\":\"teach\"", BETTER);
        }
        assertThat(events("teacher_taught")).isEqualTo(2);
    }

    /** Founder 2026-11-18: vocabulary, grammar and idioms; the line and the card name the kind. */
    @Test
    void theTeachNamesItsKind() throws Exception {
        llm.teachReply("{\"teach\":true,\"kind\":\"idiom\",\"better\":\"" + BETTER + "\",\"why\":\"People say it.\"}");
        try (TalkSocket s = open()) {
            say(s, 0);
            speak(s, 2);
            s.await(4000, "\"type\":\"teach\"", "\"kind\":\"idiom\"");
            s.await(4000, "\"caption\"", "An idiom for that");
        }
    }

    /** Founder 2026-11-18: the answer to "anything you'd like to learn today?" steers every check. */
    @Test
    void theCheckKnowsWhatTheyWantToLearn() throws Exception {
        try (TalkSocket s = open()) {
            stt.nextText("Idioms for the office please");
            say(s, 0);
            stt.nextText(null);
            speak(s, 2);
            s.await(4000, "\"type\":\"teach\"");
        }
        assertThat(llm.lastTeachRequest()).contains("Idioms for the office please");
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

    /** Review I2: "Sorry?" right after a teach repeats it, and the next turn is still the try. */
    @Test
    void aSorryRepeatsTheTeachAndKeepsTheTry() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            say(s, 2);
            stt.nextText("Sorry?");
            speak(s, 4);
            s.await(4000, "\"caption\"", "A smoother way to say that");
            s.await(4000, "\"buddy_done\"", "\"turn\":6");
            stt.nextText(BETTER);
            speak(s, 6);
            s.await(4000, "\"type\":\"judged\"", "\"result\":\"nailed\"");
            s.await(4000, "\"buddy_done\"", "\"turn\":8");
        }
        assertThat(events("teacher_judged")).isEqualTo(1);
    }

    /** Review I1: a try dropped by the crisis screen tells the web to clear the card. */
    @Test
    void aDroppedTryClearsTheCard() throws Exception {
        try (TalkSocket s = open()) {
            say(s, 0);
            say(s, 2);
            stt.nextText("this bug makes me want to die laughing");
            speak(s, 4);
            s.await(5000, "\"type\":\"teach_dropped\"");
        }
        assertThat(events("teacher_judged")).isZero();
    }

    /** Review I3 / focus 4: a pause and resume while the check runs: the late check says nothing; the resume does. */
    @Test
    void aPauseDuringTheCheckSpeaksNoLateTeachLine() throws Exception {
        llm.teachDelayMs(10);
        try (TalkSocket s = open()) {
            say(s, 0);
            speak(s, 2);
            s.json("{\"type\":\"pause\"}");
            s.json("{\"type\":\"resume\"}");
            s.await(4000, "\"caption\"", "That sounds great");
            assertThat(s.quiet(800, "A smoother way to say that")).isTrue();
        }
        assertThat(events("teacher_taught")).isZero();
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
