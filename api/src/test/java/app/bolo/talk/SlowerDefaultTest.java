package app.bolo.talk;

import app.bolo.PostgresTestBase;
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

/** SET-1 and US-2.7: a slower default from Settings, and "Slower please" asking to keep it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class SlowerDefaultTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeTextToSpeech tts;

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
        tts.voices().clear();
    }

    TalkSocket yourTurn() throws Exception {
        String id = TalkTestClient.id(client.startTalk(cookie));
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        s.json("{\"type\":\"played\",\"turn\":0}");
        s.await(2000, "\"your_turn\"");
        return s;
    }

    void speed(String speed) throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"" + speed + "\",\"captions\":true}").status()).isEqualTo(204);
    }

    @Test
    void aSlowerDefaultSpeaksTheFirstLineSlowly() throws Exception {
        speed("slower");
        try (TalkSocket s = yourTurn()) {
            assertThat(tts.voices()).isNotEmpty().allMatch(v -> v == Voice.BUDDY_SLOW);
        }
    }

    @Test
    void slowerPleaseAsksToKeepIt() throws Exception {
        stt.nextText("Slower please");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            assertThat(s.await(3000, "\"type\":\"slower\"")).isNotNull();
        }
    }

    @Test
    void slowerPleaseWhenAlreadySlowerDoesNotAskAgain() throws Exception {
        speed("slower");
        stt.nextText("Slower please");
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            assertThat(s.quiet(500, "\"type\":\"slower\"")).isTrue();
        }
    }

    @Test
    void changingSpeedMidTalkWaitsForTheNextTalk() throws Exception {
        speed("slower");
        try (TalkSocket s = yourTurn()) {
            speed("normal");
            tts.voices().clear();
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            assertThat(tts.voices()).isNotEmpty().allMatch(v -> v == Voice.BUDDY_SLOW);
        }
    }
}
