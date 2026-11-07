package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import app.bolo.speech.adapter.out.fake.FakeTextToSpeech;
import app.bolo.speech.application.port.in.Voice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P1a-4 behaviours over a real socket with fakes: silence help, repeat/slower, Bangla nudge, short answers, pause, crisis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.talk.quiet-encourage-ms=1000", "bolo.talk.quiet-hint-ms=2000", "bolo.talk.quiet-pause-ms=3000",
        "bolo.talk.no-audio-timeout-ms=2000"})
class PilotTalkAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeTextToSpeech tts;
    @Autowired FakeLlm llm;

    String cookie;
    String id;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        TalkTestClient client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        id = TalkTestClient.id(client.startTalk(cookie));
    }

    @AfterEach
    void reset() {
        stt.nextText(null);
        stt.silentRecogniser(false);
        llm.safetyReply(null);
        tts.voices().clear();
    }

    TalkSocket yourTurn() throws Exception {
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        s.json("{\"type\":\"played\",\"turn\":0}");
        s.await(2000, "\"your_turn\"");
        return s;
    }

    @Test
    void silenceGetsEncouragementThenAHintThenAPause() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.zeros(1100);
            s.await(3000, "\"buddy_done\"", "\"turn\":1");
            s.json("{\"type\":\"played\",\"turn\":1}");
            s.await(2000, "\"your_turn\"");
            s.zeros(1000);
            assertThat(s.await(3000, "\"hint\"")).contains("\"starters\"");
            s.zeros(1000);
            assertThat(s.await(3000, "\"paused\"")).contains("\"quiet\"");
        }
    }

    @Test
    void sorryRepeatsTheLastLineWithoutAskingTheModel() throws Exception {
        stt.nextText("Sorry?");
        try (TalkSocket s = yourTurn()) {
            int before = llm.replyCalls();
            s.tone(400);
            s.zeros(600);
            s.await(2000, "\"turn_end\"");
            assertThat(s.await(3000, "\"caption\"", "\"turn\":2")).contains(FakeLlm.REPLY.substring(0, 10));
            assertThat(llm.replyCalls()).isEqualTo(before);
        }
    }

    @Test
    void slowerPleaseSlowsBoloDown() throws Exception {
        stt.nextText("Slower please");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            assertThat(tts.voices()).contains(Voice.BUDDY_SLOW);
        }
    }

    @Test
    void speechWithNoWordsGetsAKindNudgeAboutEnglish() throws Exception {
        stt.silentRecogniser(true);
        try (TalkSocket s = yourTurn()) {
            s.tone(1000);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":1");
            assertThat(llm.lastReplyRequest()).contains("couldn't make out");
        }
    }

    @Test
    void aShortAnswerAsksForMore() throws Exception {
        stt.nextText("yes");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            assertThat(llm.lastReplyRequest()).contains("very short");
        }
    }

    @Test
    void pauseKeepsTheTalkAndResumeWelcomesBack() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.json("{\"type\":\"pause\"}");
            s.await(2000, "\"paused\"");
            Thread.sleep(500);
            assertThat(stt.streams().getLast().closed()).isTrue();
            Thread.sleep(2500);
            assertThat(s.quiet(100, "\"ended\"")).isTrue();
            s.json("{\"type\":\"resume\"}");
            s.await(2000, "\"resumed\"");
            s.await(3000, "\"buddy_done\"", "\"turn\":1");
            s.json("{\"type\":\"played\",\"turn\":1}");
            s.await(2000, "\"your_turn\"");
        }
    }

    @Test
    void aCrisisPhraseGetsCareAndLocalHelpNotAReply() throws Exception {
        stt.nextText("Sometimes I want to kill myself");
        llm.safetyReply("{\"crisis\":true}");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            String crisis = s.await(5000, "\"crisis\"");
            assertThat(crisis).contains("09612-119911").contains("999");
            assertThat(s.await(5000, "\"ended\"")).contains("\"crisis\"");
        }
        assertThat(jdbc.sql("select count(*) from talk_turn where session_id = ? and text like 'That sounds great%'")
                .param(UUID.fromString(id)).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from debrief where session_id = ?").param(UUID.fromString(id))
                .query(Integer.class).single()).isZero();
    }

    @Test
    void theClassifierCanFlagACrisisWithoutKeywords() throws Exception {
        llm.safetyReply("{\"crisis\":true}");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            assertThat(s.await(5000, "\"crisis\"")).contains("You're not alone");
        }
    }
}
