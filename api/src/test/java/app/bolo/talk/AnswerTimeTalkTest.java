package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.AutoAnswerTimeUseCase;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** AX-7 (changed 2026-11-14), AX-8: each talk runs at the learner's Time to answer; start delays are saved. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.talk.quiet-encourage-ms=1000", "bolo.talk.quiet-hint-ms=2000", "bolo.talk.quiet-pause-ms=3000",
        "bolo.talk.speeds.quick.encourage-ms=400", "bolo.talk.speeds.quick.hint-ms=800", "bolo.talk.speeds.quick.pause-ms=1200",
        "bolo.talk.speeds.quick.speculate-after-ms=50", "bolo.talk.speeds.quick.end-after-ms=150", "bolo.talk.speeds.quick.unfinished-end-after-ms=300",
        "bolo.talk.speeds.relaxed.encourage-ms=2500", "bolo.talk.speeds.relaxed.hint-ms=3500", "bolo.talk.speeds.relaxed.pause-ms=4500",
        "bolo.talk.speeds.relaxed.speculate-after-ms=200", "bolo.talk.speeds.relaxed.end-after-ms=600", "bolo.talk.speeds.relaxed.unfinished-end-after-ms=900",
        "bolo.talk.stammer.encourage-ms=2000", "bolo.talk.stammer.hint-ms=3000", "bolo.talk.stammer.pause-ms=4000",
        "bolo.talk.stammer.speculate-after-ms=200", "bolo.talk.stammer.end-after-ms=1000", "bolo.talk.stammer.unfinished-end-after-ms=1200",
        "bolo.talk.no-audio-timeout-ms=4000"})
class AnswerTimeTalkTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired AutoAnswerTimeUseCase autoAnswerTime;

    TalkTestClient client;
    String cookie;
    String id;

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

    void settings(String json) throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true," + json + "}").status())
                .isEqualTo(204);
    }

    TalkSocket yourTurn() throws Exception {
        id = TalkTestClient.id(client.startTalk(cookie));
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        s.json("{\"type\":\"played\",\"turn\":0}");
        s.await(2000, "\"your_turn\"");
        return s;
    }

    /** The learner turn is saved just after turn_end goes out: wait for its row. */
    Integer learnerStart() throws InterruptedException {
        return learnerStart(1);
    }

    /** The start delay of the learner's {@code nth} answer in this talk (1-based). */
    Integer learnerStart(int nth) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            var rows = jdbc.sql("select start_ms from talk_turn where session_id = ? and speaker = 'learner' order by idx")
                    .param(UUID.fromString(id)).query((rs, n) -> (Integer) rs.getObject(1)).list();
            if (rows.size() >= nth) return rows.get(nth - 1);
            Thread.sleep(50);
        }
        throw new AssertionError("learner turn " + nth + " not saved");
    }

    /** Review C1: the wait after one answer ends must not count toward the next answer's start delay. */
    @Test
    void theSecondAnswersStartDelayIsOnlyItsOwnSilence() throws Exception {
        stt.nextText("I want to tell you about my day at the office");
        try (TalkSocket s = yourTurn()) {
            s.zeros(200);
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"turn_end\"");
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"played\",\"turn\":2}");
            s.await(2000, "\"your_turn\"");
            s.zeros(700);
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"turn_end\"", "\"turn\":3");
        }
        assertThat(learnerStart(2)).isBetween(640, 800);
    }

    /** Review I1: a click or cough shorter than real speech doesn't fix the start delay; its time still counts as quiet. */
    @Test
    void aShortNoiseDoesNotFixTheStartDelay() throws Exception {
        stt.nextText("I want to tell you about my day at the office");
        try (TalkSocket s = yourTurn()) {
            s.zeros(300);
            s.tone(100);
            s.zeros(500);
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"turn_end\"");
        }
        assertThat(learnerStart()).isBetween(860, 1_000);
    }

    @Test
    void relaxedDoesNotEncourageAtTheNormalTime() throws Exception {
        settings("\"answerTime\":\"relaxed\"");
        try (TalkSocket s = yourTurn()) {
            s.zeros(1500);
            assertThat(s.quiet(700, "\"buddy_done\"", "\"turn\":1")).isTrue();
            s.zeros(1200);
            s.await(3000, "\"buddy_done\"", "\"turn\":1");
        }
    }

    @Test
    void quickEncouragesSooner() throws Exception {
        settings("\"answerTime\":\"quick\"");
        try (TalkSocket s = yourTurn()) {
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":1");
        }
    }

    @Test
    void automaticUsesBolosPick() throws Exception {
        String me = client.send("GET", "/api/me", cookie, "").body();
        autoAnswerTime.record(new LearnerId(UUID.fromString(JsonPath.read(me, "$.learnerId"))), "relaxed");
        try (TalkSocket s = yourTurn()) {
            s.zeros(1500);
            assertThat(s.quiet(700, "\"buddy_done\"", "\"turn\":1")).isTrue();
        }
    }

    @Test
    void stammerWaitsPastTheNormalTurnEnd() throws Exception {
        settings("\"stammer\":true");
        stt.nextText("I want to tell you about my day at the office");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(700);
            assertThat(s.quiet(500, "\"turn_end\"")).isTrue();
            s.zeros(500);
            s.await(3000, "\"turn_end\"");
        }
    }

    @Test
    void theStartDelayIsSavedWithTheLearnersTurn() throws Exception {
        stt.nextText("I want to tell you about my day at the office");
        try (TalkSocket s = yourTurn()) {
            s.zeros(700);
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"turn_end\"");
        }
        Integer start = learnerStart();
        assertThat(start).isBetween(640, 800);
    }

    /** Review focus 2: silence until the talk pauses, then resume and speak: the quiet time before the pause counts. */
    @Test
    void theStartDelaySurvivesTheSilencePause() throws Exception {
        stt.nextText("I want to tell you about my day at the office");
        try (TalkSocket s = yourTurn()) {
            s.zeros(1100);
            s.await(3000, "\"buddy_done\"", "\"turn\":1");
            s.json("{\"type\":\"played\",\"turn\":1}");
            s.await(2000, "\"your_turn\"");
            s.zeros(2000);
            s.await(3000, "\"paused\"");
            s.json("{\"type\":\"resume\"}");
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"played\",\"turn\":2}");
            s.await(2000, "\"your_turn\"");
            s.zeros(300);
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"turn_end\"");
        }
        Integer start = learnerStart();
        assertThat(start).isGreaterThanOrEqualTo(3_200);
    }
}
