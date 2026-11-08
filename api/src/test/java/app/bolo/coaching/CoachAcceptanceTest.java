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

    /** Founder test 2026-11-08: a long talk's quote repeated a word and the whole debrief failed, three times. */
    @Test
    void anUpgradeOfASentenceThatRepeatsAWordStillGetsADebrief() throws Exception {
        stt.nextText("I want to go to the office");
        llm.structuredReply("""
                {"strength":{"turn":1,"quote":"I want to go","text":"You said where you wanted to go."},
                 "upgrades":[{"turn":1,"said":"I want to go to the office","better":"I want to go to the office today."}],
                 "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING"}""");
        talk(3);
        String body = debriefWhenSettled();
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("ready");
        List<List<String>> changed = JsonPath.read(body, "$.fixes[?(@.kind == 'upgrade')].changed");
        assertThat(changed).containsExactly(List.of("today"));
    }

    /** PRG-8 / PRG-3: History names the talk; Progress needs every pattern found, not only the three shown. */
    @Test
    void theDebriefNamesTheTalkAndListsItsPatterns() throws Exception {
        talk(3);
        String body = debriefWhenSettled();
        assertThat((String) JsonPath.read(body, "$.topic")).isEqualTo("Saying hello to Bolo");
        List<String> patterns = JsonPath.read(body, "$.patterns[*].pattern");
        assertThat(patterns).contains("V_BH");
        List<Integer> counts = JsonPath.read(body, "$.patterns[?(@.pattern == 'V_BH')].count");
        assertThat(counts.get(0)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aMissingOrOverlongTopicFallsBackToFreeTalk() throws Exception {
        llm.structuredReply("""
                {"strength":{"turn":1,"quote":"hello bolo","text":"You greeted Bolo straight away, warmly."},
                 "upgrades":[],"sentenceLevel":"GOOD","vocabularyLevel":"BUILDING",
                 "topic":"A very long and rambling name for a talk that goes on and on and on"}""");
        talk(3);
        assertThat((String) JsonPath.read(debriefWhenSettled(), "$.topic")).isEqualTo("Free talk");
    }

    /** US-8.6 / ScreenHistorySession: Delete recording — the audio goes at once, the feedback stays. */
    @Test
    void deletingARecordingRemovesItsAudioAndKeepsTheFeedback() throws Exception {
        talk(3);
        String before = debriefWhenSettled();
        String clip = JsonPath.read(before, "$.fixes[0].clip");
        var r = client.send("POST", "/api/talk/" + id + "/recording/delete", cookie, "{}");
        assertThat(r.status()).isEqualTo(204);
        assertThat(get(clip, cookie).statusCode()).isEqualTo(404);
        String after = get("/api/talk/" + id + "/debrief", cookie).body();
        assertThat((Boolean) JsonPath.read(after, "$.recordingDeleted")).isTrue();
        assertThat((Object) JsonPath.read(after, "$.fixes[0].clip")).isNull();
        assertThat((Object) JsonPath.read(after, "$.fixes[0].model")).isNull();
        assertThat((String) JsonPath.read(after, "$.strength.text")).isNotBlank();
        assertThat(audio.children("talk/" + id)).isEmpty();
    }

    @Test
    void deletingARecordingTwiceIsFine() throws Exception {
        talk(3);
        debriefWhenSettled();
        assertThat(client.send("POST", "/api/talk/" + id + "/recording/delete", cookie, "{}").status()).isEqualTo(204);
        assertThat(client.send("POST", "/api/talk/" + id + "/recording/delete", cookie, "{}").status()).isEqualTo(204);
    }

    @Autowired app.bolo.talk.application.port.in.AudioRetentionUseCase retention;

    /** Ends the talk long ago under the given keep choice, then runs the real retention sweep. */
    void sweptAfter(boolean keep, java.time.Duration ago) {
        jdbc.sql("update talk_session set keep_audio = ?, ended_at = ? where id = ?::uuid")
                .params(keep, java.sql.Timestamp.from(java.time.Instant.now().minus(ago)), id).update();
        retention.sweep();
    }

    /** Review: once the sweep removes a no-keep talk's audio, History shows no dead players and says why. */
    @Test
    void aSweptRecordingShowsNoPlayersAndSaysItWasNotKept() throws Exception {
        talk(3);
        debriefWhenSettled();
        sweptAfter(false, java.time.Duration.ofHours(25));
        String after = get("/api/talk/" + id + "/debrief", cookie).body();
        assertThat((Object) JsonPath.read(after, "$.strength.clip")).isNull();
        assertThat((Object) JsonPath.read(after, "$.fixes[0].clip")).isNull();
        assertThat((Object) JsonPath.read(after, "$.fixes[0].model")).isNull();
        assertThat((Boolean) JsonPath.read(after, "$.recordingNotKept")).isTrue();
        assertThat(after).doesNotContain("recordingDeleted").doesNotContain("recordingExpired");
        assertThat((String) JsonPath.read(after, "$.strength.text")).isNotBlank();
    }

    /** Review: a kept recording reaches its 90 days; the debrief says it expired, not that the learner chose it. */
    @Test
    void aKeptRecordingPast90DaysSaysItExpired() throws Exception {
        talk(3);
        String before = debriefWhenSettled();
        assertThat(before).doesNotContain("recordingNotKept").doesNotContain("recordingExpired");
        sweptAfter(true, java.time.Duration.ofDays(91));
        String after = get("/api/talk/" + id + "/debrief", cookie).body();
        assertThat((Object) JsonPath.read(after, "$.fixes[0].clip")).isNull();
        assertThat((Boolean) JsonPath.read(after, "$.recordingExpired")).isTrue();
        assertThat(after).doesNotContain("recordingNotKept");
    }

    @Test
    void anotherLearnerCannotDeleteMyRecording() throws Exception {
        talk(3);
        String body = debriefWhenSettled();
        String other = client.consentedGuest(5);
        assertThat(client.send("POST", "/api/talk/" + id + "/recording/delete", other, "{}").status()).isEqualTo(404);
        assertThat(get(JsonPath.read(body, "$.fixes[0].clip"), cookie).statusCode()).isEqualTo(200);
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
        TalkTestClient.Reply r = client.send("POST", "/api/talk/" + id + "/debrief/fixes/f1/dispute", cookie, "{}");
        assertThat(r.status()).isEqualTo(204);
        List<String> kinds = JsonPath.read(get("/api/talk/" + id + "/debrief", cookie).body(), "$.fixes[*].kind");
        assertThat(kinds).containsExactly("upgrade", "upgrade");
        assertThat(jdbc.sql("select kind from fix_dispute where session_id = ?").param(UUID.fromString(id))
                .query(String.class).single()).isEqualTo("sound");
    }

    /** Review #3: two disputes of the same card at once remove that card only, and log it once. */
    @Test
    void concurrentDisputesOfOneCardRemoveOnlyIt() throws Exception {
        talk(3);
        debriefWhenSettled();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var a = pool.submit(() -> client.send("POST", "/api/talk/" + id + "/debrief/fixes/f1/dispute", cookie, "{}").status());
        var b = pool.submit(() -> client.send("POST", "/api/talk/" + id + "/debrief/fixes/f1/dispute", cookie, "{}").status());
        assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(204, 404);
        pool.shutdown();
        List<String> kinds = JsonPath.read(get("/api/talk/" + id + "/debrief", cookie).body(), "$.fixes[*].kind");
        assertThat(kinds).containsExactly("upgrade", "upgrade");
        assertThat(jdbc.sql("select count(*) from fix_dispute where session_id = ?").param(UUID.fromString(id))
                .query(Integer.class).single()).isEqualTo(1);
    }

    /** Review #4: once the debrief is settled the raw turn audio goes; only the short clips stay (until retention). */
    @Test
    void rawTurnAudioIsDeletedOnceTheDebriefIsSettled() throws Exception {
        talk(3);
        String body = debriefWhenSettled();
        assertThat(audio.get("talk/" + id + "/turn-1.wav")).isEmpty();
        String clip = JsonPath.read(body, "$.fixes[0].clip");
        assertThat(audio.get(clip.substring("/api/media/".length()))).isPresent();
    }
}
