package app.bolo.coaching;

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

/** Coach v1 end to end with fake vendors: a real talk over the socket, then the debrief and its clips. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.coaching.min-speech-ms=500", "bolo.coaching.retry-backoff-ms=100"})
class CoachAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeLlm llm;

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
    void aTalkGetsADebriefWithAStrengthOneSoundFixAndUpgrades() throws Exception {
        talk(3);
        String body = debriefWhenSettled();
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("ready");
        assertThat((String) JsonPath.read(body, "$.strength.text")).isNotBlank();
        List<String> kinds = JsonPath.read(body, "$.fixes[*].kind");
        assertThat(kinds).containsExactly("sound", "upgrade", "upgrade");
        assertThat((String) JsonPath.read(body, "$.fixes[0].title")).isEqualTo("The v sound");
        assertThat((String) JsonPath.read(body, "$.fixes[0].tip")).isEqualTo("Top teeth touch your lower lip for v.");
        assertThat((String) JsonPath.read(body, "$.skills.fluency")).isIn("BUILDING", "GOOD", "STRONG");
        assertThat((Boolean) JsonPath.read(body, "$.firstSession")).isTrue();

        String clip = JsonPath.read(body, "$.fixes[0].clip");
        String model = JsonPath.read(body, "$.fixes[0].model");
        for (String url : List.of(clip, model)) {
            HttpResponse<String> audio = get(url, cookie);
            assertThat(audio.statusCode()).as(url).isEqualTo(200);
            assertThat(audio.body()).startsWith("RIFF");
        }
    }

    @Test
    void anotherLearnerSeesNothing() throws Exception {
        talk(3);
        String body = debriefWhenSettled();
        String other = client.consentedGuest(5);
        assertThat(get("/api/talk/" + id + "/debrief", other).statusCode()).isEqualTo(404);
        assertThat(get(JsonPath.read(body, "$.fixes[0].clip"), other).statusCode()).isEqualTo(404);
    }

    @Test
    void anInventedQuoteIsDropped() throws Exception {
        llm.structuredReply("""
                {"strength":{"turn":1,"quote":"hello bolo","text":"You greeted Bolo straight away."},
                 "upgrades":[{"turn":1,"said":"I love pizza","better":"I really love pizza."},
                             {"turn":3,"said":"hello bolo","better":"Hello again, Bolo, I have more to tell you."}],
                 "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING"}""");
        talk(3);
        String body = debriefWhenSettled();
        List<String> said = JsonPath.read(body, "$.fixes[?(@.kind == 'upgrade')].said");
        assertThat(said).containsExactly("hello bolo");
        assertThat(body).doesNotContain("pizza");
    }

    @Test
    void tooLittleSpeechSaysSo() throws Exception {
        talk(1);
        assertThat((String) JsonPath.read(debriefWhenSettled(), "$.status")).isEqualTo("too_short");
    }

    @Test
    void aCoachThatKeepsFailingEndsInFailed() throws Exception {
        llm.failStructured(true);
        talk(3);
        assertThat((String) JsonPath.read(debriefWhenSettled(), "$.status")).isEqualTo("failed");
    }

    @Test
    void disputingAFixRemovesItAndLogsIt() throws Exception {
        talk(3);
        debriefWhenSettled();
        TalkTestClient.Reply r = client.send("POST", "/api/talk/" + id + "/debrief/fixes/0/dispute", cookie, "{}");
        assertThat(r.status()).isEqualTo(204);
        List<String> kinds = JsonPath.read(get("/api/talk/" + id + "/debrief", cookie).body(), "$.fixes[*].kind");
        assertThat(kinds).containsExactly("upgrade", "upgrade");
        assertThat(jdbc.sql("select kind from fix_dispute where session_id = ?").param(UUID.fromString(id))
                .query(String.class).single()).isEqualTo("sound");
    }
}
