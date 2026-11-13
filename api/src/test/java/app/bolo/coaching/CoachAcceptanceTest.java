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
        "bolo.talk.stammer.speculate-after-ms=100", "bolo.talk.stammer.end-after-ms=300", "bolo.talk.stammer.unfinished-end-after-ms=500",
        "bolo.coaching.min-speech-ms=500", "bolo.coaching.retry-backoff-ms=100", "bolo.coaching.say-per-minute=1"})
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

    /** AX-8: with I stammer on, the Coach is told not to pick or fix repeats, blocks or pauses. */
    @Test
    void theCoachIsToldAboutAStammerOnlyWhenItIsOn() throws Exception {
        llm.structuredSystems().clear();
        client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"stammer\":true}");
        talk(3);
        debriefWhenSettled();
        assertThat(llm.structuredSystems()).anyMatch(sys -> sys.contains("This learner stammers."));
    }

    @Test
    void theCoachHearsNothingAboutAStammerByDefault() throws Exception {
        llm.structuredSystems().clear();
        talk(3);
        debriefWhenSettled();
        assertThat(llm.structuredSystems()).isNotEmpty().noneMatch(sys -> sys.contains("stammer"));
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

    // ---- P1e: the full chat ------------------------------------------------------------------------------------------

    static final String CHAT_REPLY = """
            {"strength":{"turn":1,"quote":"very happy","text":"You said how you felt."},
             "upgrades":[{"turn":3,"said":"I am very happy","better":"I am really happy today."}],
             "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING","topic":"A happy day"}""";

    String chat() throws Exception {
        HttpResponse<String> r = get("/api/talk/" + id + "/chat", cookie);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return r.body();
    }

    static String slice(String chat, int mark) {
        int turn = JsonPath.read(chat, "$.marks[" + mark + "].turn");
        String text = JsonPath.read(chat, "$.turns[" + turn + "].text");
        return text.substring((Integer) JsonPath.read(chat, "$.marks[" + mark + "].start"), (Integer) JsonPath.read(chat, "$.marks[" + mark + "].end"));
    }

    @Test
    void theChatHasEveryTurnInOrder() throws Exception {
        talk(3);
        debriefWhenSettled();
        String c = chat();
        List<String> speakers = JsonPath.read(c, "$.turns[*].speaker");
        assertThat(speakers.getFirst()).isEqualTo("bolo");
        assertThat(speakers.stream().filter("learner"::equals).count()).isEqualTo(3);
        assertThat(speakers).containsOnly("bolo", "learner");
        assertThat((String) JsonPath.read(c, "$.topic")).isEqualTo("Saying hello to Bolo");
    }

    @Test
    void fixedSpotsAreMarked() throws Exception {
        stt.nextText("I am very happy");
        llm.structuredReply(CHAT_REPLY);
        talk(3);
        debriefWhenSettled();
        String c = chat();
        List<Integer> fixes = JsonPath.read(c, "$.marks[*].fix");
        assertThat(fixes).contains(1, 2);
        for (int i = 0; i < fixes.size(); i++) {
            if (fixes.get(i) == 1) assertThat(slice(c, i)).isEqualToIgnoringCase("very");
            if (fixes.get(i) == 2) assertThat(slice(c, i)).isEqualTo("I am very happy");
        }
        assertThat((String) JsonPath.read(c, "$.marks[0].title")).isNotBlank();
    }

    /** Review: the Coach's quote is accepted by words (commas ignored); the mark must be found the same way. */
    @Test
    void anUpgradeWithACommaInItIsStillMarked() throws Exception {
        stt.nextText("Yesterday, I go to market with my brother.");
        llm.structuredReply("""
                {"strength":{"turn":1,"quote":"my brother","text":"You talked about family."},
                 "upgrades":[{"turn":3,"said":"Yesterday I go to market","better":"Yesterday I went to the market."}],
                 "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING","topic":"Market day"}""");
        talk(3);
        List<String> kinds = JsonPath.read(debriefWhenSettled(), "$.fixes[*].kind");
        int upgrade = kinds.indexOf("upgrade") + 1;
        String c = chat();
        List<Integer> fixes = JsonPath.read(c, "$.marks[*].fix");
        int i = fixes.indexOf(upgrade);
        assertThat(i).as("the upgrade (fix %d) is marked", upgrade).isGreaterThanOrEqualTo(0);
        assertThat(slice(c, i)).isEqualTo("Yesterday, I go to market");
        assertThat((Integer) JsonPath.read(c, "$.marks[" + i + "].turn")).isEqualTo(3);
    }

    @Test
    void aRepeatedWordIsMarkedOnceInItsTurn() throws Exception {
        stt.nextText("I am very happy");
        llm.structuredReply(CHAT_REPLY);
        talk(3);
        debriefWhenSettled();
        List<Integer> fixes = JsonPath.read(chat(), "$.marks[*].fix");
        assertThat(fixes.stream().filter(f -> f == 1).count()).isEqualTo(1);
    }

    @Test
    void anOldDebriefFindsItsSpotsByText() throws Exception {
        stt.nextText("I am very happy");
        llm.structuredReply(CHAT_REPLY);
        talk(3);
        debriefWhenSettled();
        jdbc.sql("""
                update debrief set body = jsonb_set(body, '{fixes}',
                  (select jsonb_agg(f - 'turn') from jsonb_array_elements(body->'fixes') f)) where session_id = ?::uuid""").param(id).update();
        String c = chat();
        List<Integer> fixes = JsonPath.read(c, "$.marks[*].fix");
        assertThat(fixes).contains(1, 2);
    }

    @Test
    void anotherLearnerCannotReadMyChat() throws Exception {
        talk(3);
        debriefWhenSettled();
        assertThat(get("/api/talk/" + id + "/chat", client.consentedGuest(5)).statusCode()).isEqualTo(404);
    }

    @Test
    void noDebriefNoChat() throws Exception {
        assertThat(get("/api/talk/" + id + "/chat", cookie).statusCode()).isEqualTo(404);
    }

    // ---- P1i: drills -------------------------------------------------------------------------------------------------

    @Test
    void myTalksDrills() throws Exception {
        talk(3);
        debriefWhenSettled();
        HttpResponse<String> r = get("/api/talk/" + id + "/drills", cookie);
        assertThat(r.statusCode()).isEqualTo(200);
        List<String> kinds = JsonPath.read(r.body(), "$.drills[*].kind");
        assertThat(kinds).containsExactly("pairs", "sayBetter", "shadow");
        assertThat((String) JsonPath.read(r.body(), "$.drills[0].listen[0].target")).isEqualTo("very");
    }

    @Test
    void anotherLearnerCannotGetMyDrills() throws Exception {
        talk(3);
        debriefWhenSettled();
        String other = client.consentedGuest(5);
        assertThat(get("/api/talk/" + id + "/drills", other).statusCode()).isEqualTo(404);
        assertThat(get("/api/talk/" + id + "/drills/say?text=very", other).statusCode()).isEqualTo(404);
    }

    /** Review focus: the say endpoint is not a free text-to-speech service. */
    @Test
    void sayOnlySpeaksThisTalksTexts() throws Exception {
        talk(3);
        debriefWhenSettled();
        HttpResponse<String> ok = get("/api/talk/" + id + "/drills/say?text=very", cookie);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.headers().firstValue("content-type").orElse("")).contains("audio/wav");
        assertThat(get("/api/talk/" + id + "/drills/say?text=" + java.net.URLEncoder.encode("Buy cheap watches now", java.nio.charset.StandardCharsets.UTF_8), cookie)
                .statusCode()).isEqualTo(404);
    }

    @Test
    void shadowUsesTheSayEndpointEvenAfterTheRecordingIsDeleted() throws Exception {
        talk(3);
        debriefWhenSettled();
        client.send("POST", "/api/talk/" + id + "/recording/delete", cookie, "{}");
        String sentence = JsonPath.read(get("/api/talk/" + id + "/drills", cookie).body(), "$.drills[2].sentence");
        assertThat(get("/api/talk/" + id + "/drills/say?text=" + java.net.URLEncoder.encode(sentence, java.nio.charset.StandardCharsets.UTF_8), cookie)
                .statusCode()).isEqualTo(200);
    }

    /** Review: only new speech counts toward the limit (bolo.coaching.say-per-minute, 1 in these tests); repeats are free. */
    @Test
    void sayIsRateLimitedOnNewSpeechOnly() throws Exception {
        talk(3);
        debriefWhenSettled();
        for (int i = 0; i < 10; i++) assertThat(get("/api/talk/" + id + "/drills/say?text=very", cookie).statusCode()).isEqualTo(200);
        String drills = get("/api/talk/" + id + "/drills", cookie).body();
        int last = 0;
        for (String t : List.<String>of(JsonPath.read(drills, "$.drills[1].better"), JsonPath.read(drills, "$.drills[2].sentence"))) {
            last = get("/api/talk/" + id + "/drills/say?text=" + java.net.URLEncoder.encode(t, java.nio.charset.StandardCharsets.UTF_8), cookie).statusCode();
        }
        assertThat(last).isEqualTo(429);
    }

    /** Review: the learner's own upgraded sentence is kept with the talk (swept and deleted with it), never in the shared cache. */
    @Test
    void aDrillSentenceIsKeptWithTheTalkAndGoesWithIt() throws Exception {
        talk(3);
        debriefWhenSettled();
        String sentence = JsonPath.read(get("/api/talk/" + id + "/drills", cookie).body(), "$.drills[1].better");
        assertThat(get("/api/talk/" + id + "/drills/say?text=" + java.net.URLEncoder.encode(sentence, java.nio.charset.StandardCharsets.UTF_8), cookie)
                .statusCode()).isEqualTo(200);
        String key = "talk/" + id + "/drill-" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(sentence.getBytes(java.nio.charset.StandardCharsets.UTF_8))) + ".wav";
        assertThat(audio.get(key)).isPresent();
        client.send("POST", "/api/talk/" + id + "/recording/delete", cookie, "{}");
        assertThat(audio.get(key)).isEmpty();
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
        // The debrief is saved first, then the raw turn audio goes: wait briefly for it.
        long until = System.currentTimeMillis() + 3_000;
        while (audio.get("talk/" + id + "/turn-1.wav").isPresent() && System.currentTimeMillis() < until) Thread.sleep(50);
        assertThat(audio.get("talk/" + id + "/turn-1.wav")).isEmpty();
        String clip = JsonPath.read(body, "$.fixes[0].clip");
        assertThat(audio.get(clip.substring("/api/media/".length()))).isPresent();
    }
}
