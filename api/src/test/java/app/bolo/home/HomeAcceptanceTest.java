package app.bolo.home;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import app.bolo.speech.application.port.in.ScoredPhoneme;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.talk.TalkSocket;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Home (US-6.9) after a real talk with fake vendors: finish the feedback, then practise its sound. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.coaching.min-speech-ms=500", "bolo.coaching.retry-backoff-ms=100"})
class HomeAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeLlm llm;
    @Autowired app.bolo.media.application.port.in.AudioStoreUseCase audio;

    TalkTestClient client;
    String cookie;
    String id;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        id = TalkTestClient.id(client.startTalk(cookie));
        // "very" with a weak v (30) in a word scoring 60: a sound issue each time it is said.
        stt.nextWords(List.of(
                new ScoredWord("very", 0, 150, 60, "Mispronunciation", List.of(new ScoredPhoneme("v", 30), new ScoredPhoneme("ɛ", 90))),
                new ScoredWord("bolo", 150, 300, 95, "None", List.of(new ScoredPhoneme("b", 95)))));
    }

    @AfterEach
    void reset() {
        stt.nextWords(null);
        llm.structuredReply(null);
        llm.failStructured(false);
        stt.nextText(null);
    }

    /** Talks for {@code turns} learner turns, then ends. */
    void talk(int turns) throws Exception {
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            int buddy = 0;
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
            for (int t = 0; t < turns; t++) {
                s.json("{\"type\":\"played\",\"turn\":" + buddy + "}");
                s.await(2000, "\"your_turn\"");
                s.tone(400);
                s.zeros(600);
                buddy += 2;
                s.await(3000, "\"buddy_done\"", "\"turn\":" + buddy);
            }
            s.json("{\"type\":\"end\"}");
            s.await(2000, "\"ended\"");
        }
    }

    HttpResponse<String> get(String path, String asCookie) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", asCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    String debriefWhenSettled() throws Exception {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            HttpResponse<String> r = get("/api/talk/" + id + "/debrief", cookie);
            if (r.statusCode() == 200 && !r.body().contains("\"scoring\"")) return r.body();
            Thread.sleep(150);
        }
        throw new AssertionError("debrief not settled in 10 s");
    }

    @Test
    void afterATalkHomeAsksToFinishTheFeedback() throws Exception {
        talk(3);
        debriefWhenSettled();
        String home = get("/api/home", cookie).body();
        assertThat((String) JsonPath.read(home, "$.primary")).isEqualTo("talk");
        assertThat((String) JsonPath.read(home, "$.card.kind")).isEqualTo("feedback");
        assertThat((String) JsonPath.read(home, "$.card.talkId")).isEqualTo(id);
    }

    @Test
    void viewingItTurnsTheCardIntoPractice() throws Exception {
        talk(3);
        debriefWhenSettled();
        assertThat(client.send("POST", "/api/talk/" + id + "/debrief/viewed", cookie, "{}").status()).isEqualTo(204);
        String home = get("/api/home", cookie).body();
        assertThat((String) JsonPath.read(home, "$.card.kind")).isEqualTo("practice");
        assertThat((String) JsonPath.read(home, "$.card.pattern")).isEqualTo("V_BH");
        assertThat((String) JsonPath.read(home, "$.card.title")).isEqualTo("The v sound");
    }

    @Test
    void anotherLearnerCannotMarkItViewed() throws Exception {
        talk(3);
        debriefWhenSettled();
        String other = client.consentedGuest(5);
        assertThat(client.send("POST", "/api/talk/" + id + "/debrief/viewed", other, "{}").status()).isEqualTo(404);
        assertThat((String) JsonPath.read(get("/api/home", cookie).body(), "$.card.kind")).isEqualTo("feedback");
    }

    @Test
    void aNewLearnerHasJustTheTalkButton() throws Exception {
        String home = get("/api/home", client.consentedGuest(5)).body();
        assertThat((String) JsonPath.read(home, "$.primary")).isEqualTo("talk");
        assertThat(home).contains("\"card\":null");
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/home")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
}
