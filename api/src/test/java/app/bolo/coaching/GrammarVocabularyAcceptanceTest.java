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

/** Founder feedback 2026-11-18: the Coach adds a Grammar card and a Vocabulary card when there is one to show. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500",
        "bolo.coaching.min-speech-ms=500", "bolo.coaching.retry-backoff-ms=100", "bolo.coaching.say-per-minute=1"})
class GrammarVocabularyAcceptanceTest extends PostgresTestBase {

    static final String GRAMMAR = """
            {"turn":1,"said":"hello bolo","better":"Hello to you, Bolo.","title":"A full greeting","why":"A full sentence sounds surer."}""";
    static final String VOCABULARY = """
            {"turn":3,"said":"hello bolo","used":"hello","word":"good evening","example":"Good evening, Bolo, how was your day?","why":"Warmer at the end of the day."}""";

    static String reply(String grammar, String vocabulary) {
        return """
                {"strength":{"turn":1,"quote":"hello bolo","text":"You greeted Bolo straight away, warmly."},
                 "upgrades":[{"turn":1,"said":"hello bolo","better":"Hello Bolo, it's nice to talk with you."},
                             {"turn":3,"said":"hello bolo","better":"Hello again, Bolo, I have more to tell you."}],
                 "grammar":[%s],"vocabulary":[%s],
                 "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING","topic":"Saying hello"}""".formatted(grammar, vocabulary);
    }

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeLlm llm;

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
        llm.structuredReply(null);
        stt.nextText(null);
    }

    String talk(int turns) throws Exception {
        String id = TalkTestClient.id(client.startTalk(cookie));
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
    void theDebriefShowsGrammarAndVocabulary() throws Exception {
        llm.structuredReply(reply(GRAMMAR, VOCABULARY));
        String body = talk(3);
        List<String> kinds = JsonPath.read(body, "$.fixes[*].kind");
        assertThat(kinds).hasSizeLessThanOrEqualTo(5).containsOnlyOnce("grammar", "vocabulary");
        assertThat(kinds.indexOf("grammar")).isLessThan(kinds.indexOf("vocabulary"));
        assertThat(kinds.stream().filter("upgrade"::equals).count()).as("Say it better drops to one").isEqualTo(1);
        List<Integer> upgradeTurns = JsonPath.read(body, "$.fixes[?(@.kind == 'upgrade')].turn");
        assertThat(upgradeTurns).as("never the grammar card's sentence").containsExactly(3);
        assertThat((List<String>) JsonPath.read(body, "$.fixes[?(@.kind == 'grammar')].title")).containsExactly("A full greeting");
        assertThat((List<String>) JsonPath.read(body, "$.fixes[?(@.kind == 'grammar')].better")).containsExactly("Hello to you, Bolo.");
        assertThat((List<String>) JsonPath.read(body, "$.fixes[?(@.kind == 'vocabulary')].title")).containsExactly("\"hello\" → good evening");
        assertThat((List<String>) JsonPath.read(body, "$.fixes[?(@.kind == 'vocabulary')].better"))
                .containsExactly("Good evening, Bolo, how was your day?");
        assertThat((List<String>) JsonPath.read(body, "$.fixes[?(@.kind == 'vocabulary')].why")).containsExactly("Warmer at the end of the day.");
        List<String> model = JsonPath.read(body, "$.fixes[?(@.kind == 'vocabulary')].model");
        assertThat(model.getFirst()).isNotBlank();
        assertThat(llm.lastStructuredRequest()).contains("grammar").contains("vocabulary");
    }

    /** Review Focus: a card that isn't from the talk, or a word they didn't use, is dropped. */
    @Test
    void cardsNotFromTheTalkAreDropped() throws Exception {
        llm.structuredReply(reply(GRAMMAR.replace("\"said\":\"hello bolo\"", "\"said\":\"we shipped the report\""),
                VOCABULARY.replace("\"used\":\"hello\"", "\"used\":\"greetings\"")));
        List<String> kinds = JsonPath.read(talk(3), "$.fixes[*].kind");
        assertThat(kinds).doesNotContain("grammar", "vocabulary");
        assertThat(kinds.stream().filter("upgrade"::equals).count()).isEqualTo(2);
    }

    @Test
    void aVocabularyExampleMustUseTheWord() throws Exception {
        llm.structuredReply(reply("", VOCABULARY.replace("Good evening, Bolo, how was your day?", "Hi Bolo, how was your day?")));
        assertThat((List<String>) JsonPath.read(talk(3), "$.fixes[*].kind")).doesNotContain("vocabulary");
    }

    /** Review Focus 5: a reply without the new fields still builds today's feedback. */
    @Test
    void anOldReplyStillWorks() throws Exception {
        llm.structuredReply(null);
        List<String> kinds = JsonPath.read(talk(3), "$.fixes[*].kind");
        assertThat(kinds).doesNotContain("grammar", "vocabulary").contains("upgrade");
    }

    @Test
    void historyListsTheNewCards() throws Exception {
        llm.structuredReply(reply(GRAMMAR, VOCABULARY));
        talk(3);
        List<String> fixes = JsonPath.read(client.send("GET", "/api/history", cookie, "").body(), "$.sessions[0].fixes");
        assertThat(fixes).contains("A full greeting");
    }
}
