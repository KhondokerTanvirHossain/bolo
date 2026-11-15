package app.bolo.talk;

import app.bolo.PostgresTestBase;
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

/** SCN-1, US-7.1 (2026-11-15): Free talk or a Standup rehearsal, saved on the learner and copied onto each talk. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=50", "bolo.talk.starts-per-minute=60", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake"})
class ScenarioSettingAcceptanceTest extends PostgresTestBase {

    static final String BASE = "\"buddySpeed\":\"normal\",\"captions\":true";

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;
    String cookie;
    UUID learner;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        learner = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId"));
    }

    void put(String fields) throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{" + BASE + fields + "}").status()).isEqualTo(204);
    }

    String me(String path) throws Exception {
        return JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), path);
    }

    UUID start() throws Exception {
        var r = client.startTalk(cookie);
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return UUID.fromString(TalkTestClient.id(r));
    }

    Map<String, Object> row(UUID talk) {
        return jdbc.sql("select scenario, difficulty from talk_session where id = ?").param(talk).query().singleRow();
    }

    int events(String name) {
        return jdbc.sql("select count(*) from event where learner_id = ? and name = ?").params(learner, name).query(Integer.class).single();
    }

    @Test
    void theDefaultIsFreeTalk() throws Exception {
        put("");
        assertThat(me("$.settings.scenario")).isEqualTo("free");
        assertThat(me("$.settings.difficulty")).isEqualTo("realistic");
    }

    @Test
    void standupIsSavedAndCopiedOntoTheNextTalk() throws Exception {
        put(",\"scenario\":\"standup\",\"difficulty\":\"friendly\"");
        var r = row(start());
        assertThat(r.get("scenario")).isEqualTo("standup");
        assertThat(r.get("difficulty")).isEqualTo("friendly");
    }

    @Test
    void aFreeTalkHasNoDifficulty() throws Exception {
        put("");
        var r = row(start());
        assertThat(r.get("scenario")).isEqualTo("free");
        assertThat(r.get("difficulty")).isNull();
    }

    @Test
    void theStartResponseNamesTheScenario() throws Exception {
        put(",\"scenario\":\"standup\"");
        String body = client.startTalk(cookie).body();
        assertThat((String) JsonPath.read(body, "$.scenario")).isEqualTo("standup");
        assertThat((String) JsonPath.read(body, "$.difficulty")).isEqualTo("realistic");
    }

    @Autowired app.bolo.talk.application.port.in.StartTalkUseCase talks;

    /** Review Focus 1: the open talk keeps what it started with. */
    @Test
    void aChangeDuringATalkDoesNotChangeIt() throws Exception {
        put("");
        UUID id = start();
        put(",\"scenario\":\"standup\"");
        assertThat(row(id).get("scenario")).isEqualTo("free");
        var view = talks.openFor(new app.bolo.common.id.LearnerId(learner), new app.bolo.common.id.TalkSessionId(id)).orElseThrow();
        assertThat(view.scenario()).isEqualTo("free");
    }

    /** Review Focus 2: an older web page that doesn't send the fields keeps them. */
    @Test
    void anOldClientWithoutTheFieldsKeepsThem() throws Exception {
        put(",\"scenario\":\"standup\",\"difficulty\":\"friendly\"");
        put("");
        assertThat(me("$.settings.scenario")).isEqualTo("standup");
        assertThat(me("$.settings.difficulty")).isEqualTo("friendly");
    }

    @Test
    void anUnknownScenarioIsRefused() throws Exception {
        var r = client.send("PUT", "/api/me/settings", cookie, "{" + BASE + ",\"scenario\":\"interview\"}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("scenario");
        var d = client.send("PUT", "/api/me/settings", cookie, "{" + BASE + ",\"difficulty\":\"hard\"}");
        assertThat(d.status()).isEqualTo(422);
        assertThat(d.body()).contains("difficulty");
    }

    @Test
    void changingScenarioIsAnEvent() throws Exception {
        put(",\"scenario\":\"standup\"");
        put(",\"scenario\":\"standup\"");
        assertThat(events("scenario_changed")).isEqualTo(1);
        String props = jdbc.sql("select props::text from event where learner_id = ? and name = 'scenario_changed'").param(learner)
                .query(String.class).single();
        assertThat(props).contains("\"from\": \"free\"").contains("\"to\": \"standup\"");
    }

    @Test
    void sessionStartedCarriesTheScenario() throws Exception {
        put(",\"scenario\":\"standup\"");
        UUID id = start();
        try (TalkSocket s = new TalkSocket(port, id.toString(), cookie)) {
            s.await(3000, "\"buddy_done\"");
        }
        String props = jdbc.sql("select props::text from event where learner_id = ? and name = 'session_started'").param(learner)
                .query(String.class).single();
        assertThat(props).contains("\"scenario\": \"standup\"").contains("\"difficulty\": \"realistic\"");
    }

    /** Review I4: the choice sent with the start wins over a settings save still on its way. */
    @Test
    void theStartsChoiceWins() throws Exception {
        put("");
        var r = client.send("POST", "/api/talk", cookie, "{\"scenario\":\"standup\",\"difficulty\":\"friendly\"}");
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        var row = row(UUID.fromString(TalkTestClient.id(r)));
        assertThat(row.get("scenario")).isEqualTo("standup");
        assertThat(row.get("difficulty")).isEqualTo("friendly");
    }

    @Test
    void aStartWithAnUnknownScenarioUsesTheSettings() throws Exception {
        put(",\"scenario\":\"standup\"");
        var r = client.send("POST", "/api/talk", cookie, "{\"scenario\":\"interview\"}");
        assertThat(r.status()).isEqualTo(201);
        assertThat(row(UUID.fromString(TalkTestClient.id(r))).get("scenario")).isEqualTo("standup");
    }
}
