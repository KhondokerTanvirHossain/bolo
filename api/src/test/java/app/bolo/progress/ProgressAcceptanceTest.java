package app.bolo.progress;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-1/3/8: Progress and History show only the learner's own talks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgressAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    UUID learnerOf(String cookie) throws Exception {
        return UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId"));
    }

    void pastTalk(UUID learner, int daysAgo, String topic, String pattern) {
        UUID talk = UUID.randomUUID();
        Instant at = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        jdbc.sql("insert into talk_session (id, learner_id, minutes, started_at, ended_at, end_reason) values (?, ?, 5, ?, ?, 'learner_ended')")
                .params(talk, learner, Timestamp.from(at), Timestamp.from(at.plusSeconds(300))).update();
        String body = """
                {"status":"ready","topic":"%s","speechMs":120000,
                 "skills":{"pronunciation":"GOOD","fluency":"BUILDING","sentenceBuilding":"GOOD","vocabulary":"STRONG"},
                 "fixes":[{"id":"f1","kind":"sound","pattern":"%s","title":"The v sound"}],
                 "patterns":[{"pattern":"%s","title":"The v sound","count":3}]}""".formatted(topic, pattern, pattern);
        jdbc.sql("insert into debrief (session_id, status, body, updated_at) values (?, 'ready', ?::jsonb, now())").params(talk, body).update();
    }

    @Test
    void myProgressShowsMyTalksOnly() throws Exception {
        String me = client.consentedGuest(5);
        UUID id = learnerOf(me);
        pastTalk(id, 3, "Job interview practice", "V_BH");
        pastTalk(id, 2, "My weekend", "V_BH");
        pastTalk(id, 1, "The payment bug demo", "V_BH");
        String other = client.consentedGuest(5);
        pastTalk(learnerOf(other), 1, "Someone else", "TH_TD");

        String p = client.send("GET", "/api/progress", me, "").body();
        assertThat((Integer) JsonPath.read(p, "$.effort.talks")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(p, "$.effort.minutes")).isEqualTo(6);
        assertThat((List<?>) JsonPath.read(p, "$.trends.skills.fluency")).hasSize(3);
        assertThat((String) JsonPath.read(p, "$.patterns.active[0].key")).isEqualTo("V_BH");
        assertThat((List<String>) JsonPath.read(p, "$.latest[*].topic")).containsExactly("The payment bug demo", "My weekend", "Job interview practice");
        assertThat(p).doesNotContain("Someone else").doesNotContain("TH_TD");
    }

    @Test
    void historyListsMyTalksNewestFirst() throws Exception {
        String me = client.consentedGuest(5);
        UUID id = learnerOf(me);
        pastTalk(id, 5, "First", "V_BH");
        pastTalk(id, 1, "Latest", "V_BH");
        String h = client.send("GET", "/api/history", me, "").body();
        assertThat((List<String>) JsonPath.read(h, "$.sessions[*].topic")).containsExactly("Latest", "First");
        assertThat((List<String>) JsonPath.read(h, "$.sessions[0].fixes")).containsExactly("The v sound");
    }

    @Test
    void aNewLearnerHasNothingYet() throws Exception {
        String p = client.send("GET", "/api/progress", client.consentedGuest(5), "").body();
        assertThat((Integer) JsonPath.read(p, "$.effort.talks")).isZero();
        assertThat((Integer) JsonPath.read(p, "$.trends.sessions")).isZero();
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("GET", "/api/progress", null, "").status()).isEqualTo(401);
        assertThat(client.send("GET", "/api/history", null, "").status()).isEqualTo(401);
    }
}
