package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.speech.adapter.out.fake.FakeBanglaSpeech;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Founder feedback 2026-11-18: Bolo understands Bangla. A turn the English recogniser is unsure of (or heard no words
 * in) is recognised once in Bangla; a Bangla turn is shown in Bangla script and Bolo answers in English with the English
 * way to say it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.talk.bangla.timeout-ms=600", "bolo.talk.teacher.timeout-ms=500"})
class BanglaTalkTest extends PostgresTestBase {

    static final String BANGLA = "আমার বস শেষ মুহূর্তে পুরো পরিকল্পনা বদলে দিয়েছে।";
    static final String ROMANISED = "Amar boss shesh mourte puro pori kolpana bodle dietze";

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeBanglaSpeech bangla;
    @Autowired FakeLlm llm;
    TalkTestClient client;
    String cookie;
    String id;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        bangla.reset();
        bangla.reply(BANGLA, 0.85);
    }

    @AfterEach
    void reset() {
        stt.nextText(null);
        stt.nextConfidence(null);
        stt.silentRecogniser(false);
        bangla.reset();
        llm.teachReply(null);
        llm.failSafety(false);
        llm.safetyReply(null);
    }

    TalkSocket yourTurn() throws Exception {
        id = TalkTestClient.id(client.startTalk(cookie));
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        s.json("{\"type\":\"played\",\"turn\":0}");
        s.await(2000, "\"your_turn\"");
        return s;
    }

    void speak(TalkSocket s) throws Exception {
        s.tone(400);
        s.zeros(600);
    }

    String savedText(int idx) {
        return jdbc.sql("select text from talk_turn where session_id = ? and idx = ?").param(UUID.fromString(id)).param(idx)
                .query(String.class).single();
    }

    @Test
    void anUnsureTurnIsHeardInBanglaAndAnsweredInEnglish() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(3000, "\"turn_end\"", BANGLA);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        assertThat(savedText(1)).isEqualTo(BANGLA);
        assertThat(bangla.calls()).isEqualTo(1);
        assertThat(llm.lastReplyRequest()).contains(BANGLA).contains("in Bangla").contains("In English").contains("never in Bangla");
    }

    /** Review Focus 2: a confident English turn never calls the Bangla recogniser. */
    @Test
    void aConfidentEnglishTurnIsNeverChecked() throws Exception {
        stt.nextConfidence(0.88);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        assertThat(bangla.calls()).isZero();
        assertThat(savedText(1)).isEqualTo("hello bolo");
        assertThat(llm.lastReplyRequest()).doesNotContain("in Bangla");
    }

    /** The check starts when the learner pauses (with the speculative reply), so the end of the turn waits less. */
    @Test
    void theCheckStartsAtThePauseAndIsUsedOnce() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.6);
        // 700 ms is longer than the 600 ms the end of the turn waits: only a check started at the pause finishes in time.
        bangla.delayMs(700);
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(200);
            Thread.sleep(600); // the learner pauses in real time: the speculative reply and the Bangla check start here
            s.zeros(400);
            s.await(3000, "\"turn_end\"", BANGLA);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        assertThat(bangla.calls()).as("the early check is used, not repeated").isEqualTo(1);
    }

    @Test
    void speechWithNoWordsIsCheckedInBangla() throws Exception {
        stt.silentRecogniser(true);
        try (TalkSocket s = yourTurn()) {
            s.tone(1000);
            s.zeros(600);
            s.await(3000, "\"turn_end\"", BANGLA);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        assertThat(llm.lastReplyRequest()).contains("In English").doesNotContain("couldn't make out");
    }

    /** Review Focus 1: a slow or failing Bangla check means the turn goes on as today. */
    @Test
    void aSlowCheckGoesOnAsToday() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        bangla.delayMs(3000);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        assertThat(savedText(1)).isEqualTo(ROMANISED);
        assertThat(llm.lastReplyRequest()).doesNotContain("in Bangla");
    }

    @Test
    void aFailedCheckOnAWordlessTurnGivesTheKindNudge() throws Exception {
        stt.silentRecogniser(true);
        bangla.fail(true);
        try (TalkSocket s = yourTurn()) {
            s.tone(1000);
            s.zeros(600);
            s.await(4000, "\"buddy_done\"", "\"turn\":1");
        }
        assertThat(llm.lastReplyRequest()).contains("couldn't make out");
    }

    @Test
    void englishLettersBackMeansNotBangla() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.7);
        bangla.reply(null, 0);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        assertThat(savedText(1)).isEqualTo(ROMANISED);
    }

    /** Review Focus 4: a Bangla turn's English-recogniser words are never scored. */
    @Test
    void aBanglaTurnIsNeverScored() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(4000, "\"buddy_done\"", "\"turn\":2");
        }
        String scored = jdbc.sql("select coalesce(scored::text, '[]') from talk_turn where session_id = ? and idx = 1")
                .param(UUID.fromString(id)).query(String.class).single();
        assertThat(scored).isIn("[]", "null");
    }

    /** Review Focus 3: a pause while the check runs: nothing late is spoken. */
    @Test
    void aPauseDuringTheCheckSpeaksNothingLate() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        bangla.delayMs(300);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.json("{\"type\":\"pause\"}");
            assertThat(s.quiet(900, "\"turn_end\"", BANGLA)).isTrue();
        }
    }

    /** The English teacher teaches the English sentence for a Bangla turn, labelled "english". */
    @Test
    void theTeacherTeachesTheEnglish() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"teacher\"}")
                .status()).isEqualTo(204);
        llm.teachReply("{\"teach\":true,\"kind\":\"grammar\",\"better\":\"My boss changed the whole plan at the last minute.\",\"why\":\"Here it is in English.\"}");
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(4000, "\"type\":\"teach\"", "\"kind\":\"english\"");
            s.await(4000, "\"caption\"", "In English, you can say");
        }
        assertThat(llm.lastTeachRequest()).contains("Bangla");
    }

    /**
     * Review 2026-11-18 (Critical 1): the keyword screen can't read Bangla script, so every Bangla turn waits for the
     * safety classifier before Bolo says anything, and a classifier that fails counts as a crisis (fail closed).
     */
    @Test
    void aBanglaTurnWaitsForTheSafetyCheckAndFailsClosed() throws Exception {
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        llm.failSafety(true);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            assertThat(s.await(5000, "\"crisis\"")).contains("You're not alone");
        }
        assertThat(llm.lastReplyRequest()).as("Bolo never answered the Bangla turn").doesNotContain(BANGLA);
    }

    @Test
    void aBanglaCrisisInTheTeacherHatIsNeverTaught() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"teacher\"}")
                .status()).isEqualTo(204);
        llm.teachReply("{\"teach\":true,\"kind\":\"grammar\",\"better\":\"I feel very sad.\",\"why\":\"Here it is in English.\"}");
        llm.safetyReply("{\"crisis\":true}");
        stt.nextText(ROMANISED);
        stt.nextConfidence(0.16);
        try (TalkSocket s = yourTurn()) {
            speak(s);
            s.await(5000, "\"crisis\"");
            assertThat(s.quiet(500, "\"type\":\"teach\"")).isTrue();
        }
    }
}
