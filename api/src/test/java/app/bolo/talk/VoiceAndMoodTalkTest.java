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

import static org.assertj.core.api.Assertions.assertThat;

/** P3c (SET-5, SET-6, 2026-11-13): a talk speaks in the voice and mood chosen in Settings, fixed when it starts. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class VoiceAndMoodTalkTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeTextToSpeech tts;
    @Autowired FakeLlm llm;

    TalkTestClient client;
    String cookie;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        tts.voices().clear();
        tts.buddyVoices().clear();
    }

    @AfterEach
    void reset() {
        stt.nextText(null);
    }

    void settings(String json) throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, json).status()).isEqualTo(204);
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
    void theTalkSpeaksInTheChosenVoice() throws Exception {
        settings("{\"buddySpeed\":\"normal\",\"captions\":true,\"voiceGender\":\"male\",\"voiceAccent\":\"american\"}");
        try (TalkSocket s = yourTurn()) {
            assertThat(tts.buddyVoices()).isNotEmpty().allMatch("american-male"::equals);
        }
    }

    @Test
    void theDefaultIsBritishFemale() throws Exception {
        try (TalkSocket s = yourTurn()) {
            assertThat(tts.buddyVoices()).isNotEmpty().allMatch("british-female"::equals);
        }
    }

    @Test
    void theMoodReachesBolo() throws Exception {
        settings("{\"buddySpeed\":\"normal\",\"captions\":true,\"mood\":\"funny\"}");
        try (TalkSocket s = yourTurn()) {
            assertThat(llm.lastReplyRequest()).contains("Your mood is funny");
        }
    }

    @Test
    void slowerKeepsTheChosenVoice() throws Exception {
        settings("{\"buddySpeed\":\"slower\",\"captions\":true,\"voiceGender\":\"female\",\"voiceAccent\":\"indian\"}");
        try (TalkSocket s = yourTurn()) {
            assertThat(tts.voices()).isNotEmpty().allMatch(v -> v == Voice.BUDDY_SLOW);
            assertThat(tts.buddyVoices()).isNotEmpty().allMatch("indian-female"::equals);
        }
    }

    @Test
    void aTalkKeepsTheVoiceItStartedWith() throws Exception {
        try (TalkSocket s = yourTurn()) {
            settings("{\"buddySpeed\":\"normal\",\"captions\":true,\"voiceGender\":\"male\"}");
            tts.buddyVoices().clear();
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            assertThat(tts.buddyVoices()).isNotEmpty().allMatch("british-female"::equals);
        }
    }
}
