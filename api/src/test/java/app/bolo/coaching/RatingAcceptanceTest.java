package app.bolo.coaching;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** DBF-8 (US-4.11 changed 2026-11-13: a typed note): "How was today?" after the feedback of some talks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;
    String cookie;
    UUID learner;
    int talks;

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        learner = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId"));
        talks = 0;
    }

    /** The learner's next finished talk, its feedback ready. */
    String talk() {
        UUID id = UUID.randomUUID();
        talks++;
        jdbc.sql("""
                insert into talk_session (id, learner_id, minutes, started_at, ended_at, end_reason)
                values (?, ?, 5, now() - make_interval(hours => 100 - ?), now(), 'time_up')""").params(id, learner, talks).update();
        jdbc.sql("insert into debrief (session_id, status, body, updated_at) values (?, 'ready', '{\"status\":\"ready\"}'::jsonb, now())")
                .param(id).update();
        return id.toString();
    }

    boolean asks(String talk) throws Exception {
        var r = client.send("GET", "/api/talk/" + talk + "/rating", cookie, "");
        assertThat(r.status()).isEqualTo(200);
        return JsonPath.read(r.body(), "$.ask");
    }

    TalkTestClient.Reply rate(String talk, String json) throws Exception {
        return client.send("PUT", "/api/talk/" + talk + "/rating", cookie, json);
    }

    Map<String, Object> row(String talk) {
        return jdbc.sql("select score, note from talk_rating where talk_id = ?").param(UUID.fromString(talk)).query().singleRow();
    }

    @Test
    void theFirstTalkNeverAsks() throws Exception {
        assertThat(asks(talk())).isFalse();
    }

    @Test
    void theSecondTalkAsks() throws Exception {
        talk();
        assertThat(asks(talk())).isTrue();
    }

    @Test
    void theFifthTalkDoesNotAskButTheSeventhDoes() throws Exception {
        for (int i = 0; i < 4; i++) talk();
        assertThat(asks(talk())).isFalse();
        talk();
        assertThat(asks(talk())).isTrue();
    }

    @Test
    void aRatedTalkNeverAsksAgainAndKeepsScoreAndNote() throws Exception {
        talk();
        String t = talk();
        assertThat(rate(t, "{\"score\":4}").status()).isEqualTo(204);
        assertThat(rate(t, "{\"score\":4,\"note\":\"  Bolo was fun  \"}").status()).isEqualTo(204);
        assertThat(asks(t)).isFalse();
        assertThat(row(t)).containsEntry("score", 4).containsEntry("note", "Bolo was fun");
    }

    @Test
    void notNowSkipsAndNeverAsksAgain() throws Exception {
        talk();
        String t = talk();
        assertThat(rate(t, "{\"score\":null}").status()).isEqualTo(204);
        assertThat(asks(t)).isFalse();
        assertThat(row(t).get("score")).isNull();
    }

    /** Review focus 4: "Not now" after a face was tapped keeps the score. */
    @Test
    void aNullScoreKeepsTheSavedScore() throws Exception {
        talk();
        String t = talk();
        rate(t, "{\"score\":5}");
        rate(t, "{\"score\":null}");
        assertThat(row(t)).containsEntry("score", 5);
    }

    /** Review I2: a face tap arriving after Send (or any score-only save) keeps the note. */
    @Test
    void aScoreOnlySaveKeepsTheNote() throws Exception {
        talk();
        String t = talk();
        rate(t, "{\"score\":4,\"note\":\"Bolo was fun\"}");
        rate(t, "{\"score\":4}");
        assertThat(row(t)).containsEntry("score", 4).containsEntry("note", "Bolo was fun");
    }

    @Test
    void anEmptyNoteIsNoNote() throws Exception {
        talk();
        String t = talk();
        rate(t, "{\"score\":3,\"note\":\"   \"}");
        assertThat(row(t).get("note")).isNull();
    }

    @Test
    void aNoteWithoutAScoreIs422Note() throws Exception {
        var r = rate(talk(), "{\"note\":\"hi\"}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("note");
    }

    @Test
    void aLongNoteIs422Note() throws Exception {
        var r = rate(talk(), "{\"score\":3,\"note\":\"" + "a".repeat(301) + "\"}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("note");
    }

    @Test
    void aScoreOutsideOneToFiveIs422Score() throws Exception {
        var r = rate(talk(), "{\"score\":6}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("score");
        assertThat(rate(talk(), "{\"score\":0}").status()).isEqualTo(422);
    }

    @Test
    void someoneElsesTalkIs404() throws Exception {
        String t = talk();
        String other = client.consentedGuest(5);
        assertThat(client.send("GET", "/api/talk/" + t + "/rating", other, "").status()).isEqualTo(404);
        assertThat(client.send("PUT", "/api/talk/" + t + "/rating", other, "{\"score\":1}").status()).isEqualTo(404);
        assertThat(client.send("GET", "/api/talk/not-a-uuid/rating", cookie, "").status()).isEqualTo(404);
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("GET", "/api/talk/" + talk() + "/rating", null, "").status()).isEqualTo(401);
    }

    /** Review focus 5: a guest's rating moves to the account they sign into. */
    @Test
    void aGuestsRatingMovesOnSignIn() throws Exception {
        String u = "u" + UUID.randomUUID().toString().substring(0, 8);
        String account = client.send("POST", "/api/auth/google", client.consentedGuest(5),
                "{\"idToken\":\"fake:" + u + ":" + u + "@example.com\"}").cookie();
        UUID accountId = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", account, "").body(), "$.learnerId"));
        talk();
        String t = talk();
        rate(t, "{\"score\":5,\"note\":\"great\"}");
        var r = client.send("POST", "/api/auth/google", cookie, "{\"idToken\":\"fake:" + u + ":" + u + "@example.com\"}");
        assertThat(r.status()).isEqualTo(200);
        assertThat(jdbc.sql("select learner_id from talk_rating where talk_id = ?").param(UUID.fromString(t)).query(UUID.class).single())
                .isEqualTo(accountId);
    }
}
