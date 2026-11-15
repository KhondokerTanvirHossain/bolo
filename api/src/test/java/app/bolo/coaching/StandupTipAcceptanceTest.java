package app.bolo.coaching;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SCN-2, US-7.3, ASM-4 (2026-11-15): a Standup rehearsal's debrief has one standup tip. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.coaching.min-speech-ms=500", "bolo.coaching.retry-backoff-ms=100", "bolo.coaching.say-per-minute=1"})
class StandupTipAcceptanceTest extends PostgresTestBase {

    static String reply(String tip) {
        return """
                {"strength":{"turn":1,"quote":"hello bolo","text":"You greeted Bolo straight away, warmly."},
                 "upgrades":[{"turn":1,"said":"hello bolo","better":"Hello Bolo, it's nice to talk with you."}],
                 "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING","topic":"My morning update"%s}""".formatted(tip == null ? "" : ",\"standupTip\":" + tip);
    }

    static final String GOOD_TIP = """
            {"rule":"give_a_date","turn":3,"said":"hello bolo","better":"Hello Bolo, it will be ready by Thursday.","why":"A manager plans around your date."}""";

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
    }

    @AfterEach
    void reset() {
        llm.structuredReply(null);
        stt.nextText(null);
    }

    void scenario(String s) throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"scenario\":\"" + s + "\"}")
                .status()).isEqualTo(204);
    }

    String talk(int turns) throws Exception {
        id = TalkTestClient.id(client.startTalk(cookie));
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
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            var r = client.send("GET", "/api/talk/" + id + "/debrief", cookie, "");
            if (r.status() == 200 && !r.body().contains("\"scoring\"")) return r.body();
            Thread.sleep(150);
        }
        throw new AssertionError("debrief not settled in 10 s");
    }

    @Test
    void aStandupDebriefShowsOneTip() throws Exception {
        scenario("standup");
        llm.structuredReply(reply(GOOD_TIP));
        String body = talk(3);
        List<String> kinds = JsonPath.read(body, "$.fixes[*].kind");
        assertThat(kinds).hasSizeLessThanOrEqualTo(3).containsOnlyOnce("standup");
        assertThat(kinds.getLast()).isEqualTo("standup");
        List<String> titles = JsonPath.read(body, "$.fixes[?(@.kind == 'standup')].title");
        assertThat(titles).containsExactly("Give a date, not \"soon\"");
        List<String> why = JsonPath.read(body, "$.fixes[?(@.kind == 'standup')].why");
        assertThat(why).containsExactly("A manager plans around your date.");
        List<String> model = JsonPath.read(body, "$.fixes[?(@.kind == 'standup')].model");
        assertThat(model.getFirst()).isNotBlank();
        assertThat(llm.lastStructuredRequest()).contains("standupTip").contains("give_a_date");
    }

    /** Review Focus 4: a tip that isn't from the talk, or names an unknown rule, is dropped. */
    @Test
    void aTipNotFromTheTranscriptIsDropped() throws Exception {
        scenario("standup");
        llm.structuredReply(reply("""
                {"rule":"give_a_date","turn":3,"said":"we shipped the refund report","better":"We shipped it on Thursday.","why":"Dates help."}"""));
        List<String> kinds = JsonPath.read(talk(3), "$.fixes[*].kind");
        assertThat(kinds).doesNotContain("standup");
    }

    @Test
    void aTipWithAnUnknownRuleIsDropped() throws Exception {
        scenario("standup");
        llm.structuredReply(reply(GOOD_TIP.replace("give_a_date", "be_louder")));
        List<String> kinds = JsonPath.read(talk(3), "$.fixes[*].kind");
        assertThat(kinds).doesNotContain("standup");
    }

    /** Review Focus 5: a free talk never has a tip, even if the model returns one. */
    @Test
    void freeTalkNeverHasATip() throws Exception {
        scenario("free");
        llm.structuredReply(reply(GOOD_TIP));
        List<String> kinds = JsonPath.read(talk(3), "$.fixes[*].kind");
        assertThat(kinds).doesNotContain("standup");
        assertThat(llm.lastStructuredRequest()).doesNotContain("standupTip");
    }

    @Test
    void historyTagsAStandup() throws Exception {
        scenario("standup");
        llm.structuredReply(reply(GOOD_TIP));
        talk(3);
        String history = client.send("GET", "/api/history", cookie, "").body();
        assertThat((String) JsonPath.read(history, "$.sessions[0].scenario")).isEqualTo("standup");
        List<String> fixes = JsonPath.read(history, "$.sessions[0].fixes");
        assertThat(fixes).contains("Give a date, not \"soon\"");
    }
}
