package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Hats and 10-minute talks (2026-11-16): teacher and ielts scenarios; 10 minutes for Premium and Ultimate only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.talk.max-live-sessions=50",
        "bolo.talk.starts-per-minute=60", "bolo.firebase.api-key=k", "bolo.firebase.auth-domain=d", "bolo.firebase.project-id=p",
        "bolo.firebase.app-id=a"})
class HatsAndLengthAcceptanceTest extends PostgresTestBase {

    static final String BASE = "\"buddySpeed\":\"normal\",\"captions\":true";

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired app.bolo.talk.application.port.in.PlansUseCase plans;
    TalkTestClient client;
    String me;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        String guest = client.consentedGuest(10);
        me = client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"fake:fp" + UUID.randomUUID() + ":a@x.com\"}").cookie();
    }

    void put(String fields) throws Exception {
        var r = client.send("PUT", "/api/me/settings", me, "{" + BASE + fields + "}");
        assertThat(r.status()).as(r.body()).isEqualTo(204);
    }

    LearnerId learner() throws Exception {
        return new LearnerId(UUID.fromString(JsonPath.read(client.send("GET", "/api/me", me, "").body(), "$.learnerId")));
    }

    String start(String body) throws Exception {
        var r = client.send("POST", "/api/talk", me, body);
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.body();
    }

    int minutesOf(String startBody) {
        return JsonPath.read(startBody, "$.minutes");
    }

    String scenarioOf(String startBody) {
        return jdbc.sql("select scenario from talk_session where id = ?").param(UUID.fromString(JsonPath.read(startBody, "$.id")))
                .query(String.class).single();
    }

    @Test
    void teacherAndIeltsAreSavedAndCopied() throws Exception {
        put(",\"scenario\":\"teacher\"");
        assertThat(scenarioOf(start("{}"))).isEqualTo("teacher");
        put(",\"scenario\":\"ielts\"");
        assertThat(scenarioOf(start("{}"))).isEqualTo("ielts");
        assertThat(scenarioOf(start("{\"scenario\":\"teacher\"}"))).isEqualTo("teacher");
    }

    @Test
    void aFreeLearnerTalksFiveMinutesEvenAskingForTen() throws Exception {
        put(",\"talkMinutes\":10");
        assertThat(minutesOf(start("{\"minutes\":10}"))).isEqualTo(5);
    }

    @Test
    void basicIsFiveMinutes() throws Exception {
        plans.grant(learner(), "basic", 30, "admin", "test");
        put(",\"talkMinutes\":10");
        assertThat(minutesOf(start("{}"))).isEqualTo(5);
    }

    @Test
    void premiumChoosesTenAndItCountsAsOneTalk() throws Exception {
        plans.grant(learner(), "premium", 30, "admin", "test");
        put(",\"talkMinutes\":10");
        assertThat(minutesOf(start("{}"))).isEqualTo(10);
        assertThat(minutesOf(start("{\"minutes\":5}"))).isEqualTo(5);
        jdbc.sql("update talk_session set attached_at = now() - interval '5 minutes', ended_at = now(), end_reason = 'time_up' "
                + "where learner_id = ?").param(learner().value()).update();
        int left = JsonPath.read(client.send("GET", "/api/home", me, "").body(), "$.freeTalks.left");
        int total = JsonPath.read(client.send("GET", "/api/home", me, "").body(), "$.freeTalks.total");
        assertThat(total - left).isEqualTo(2);
    }

    @Test
    void ultimateChoosesTen() throws Exception {
        plans.grant(learner(), "ultimate", 30, "admin", "test");
        assertThat(minutesOf(start("{\"minutes\":10}"))).isEqualTo(10);
    }

    /** Review Focus 5: an old client without talkMinutes keeps it. */
    @Test
    void anOldClientKeepsTheLength() throws Exception {
        put(",\"talkMinutes\":10");
        put("");
        assertThat((Integer) JsonPath.read(client.send("GET", "/api/me", me, "").body(), "$.settings.talkMinutes")).isEqualTo(10);
    }

    @Test
    void anUnknownLengthIsRefused() throws Exception {
        var r = client.send("PUT", "/api/me/settings", me, "{" + BASE + ",\"talkMinutes\":7}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("talk-minutes");
        var s = client.send("PUT", "/api/me/settings", me, "{" + BASE + ",\"scenario\":\"interview\"}");
        assertThat(s.status()).isEqualTo(422);
    }
}
