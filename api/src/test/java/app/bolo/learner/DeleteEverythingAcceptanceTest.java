package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-4 / US-8.6: Me → Privacy → Delete everything (type DELETE) removes the learner and all their data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.talk.max-live-sessions=50")
class DeleteEverythingAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired AudioStoreUseCase audio;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
    }

    record Person(String cookie, UUID id, String talk) {
    }

    /** An account with a talk, its debrief and clips, a practice attempt, an event, usage and a Day 1 clip. */
    Person person() throws Exception {
        String cookie = client.send("POST", "/api/auth/google", client.consentedGuest(5),
                "{\"idToken\":\"fake:del" + UUID.randomUUID() + ":a@x.com\"}").cookie();
        UUID id = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId"));
        String talk = TalkTestClient.id(client.startTalk(cookie));
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where id = ?").param(UUID.fromString(talk)).update();
        jdbc.sql("insert into debrief (session_id, status, body, updated_at) values (?, 'ready', '{}'::jsonb, now())").param(UUID.fromString(talk)).update();
        jdbc.sql("insert into attempt (learner_id, reference, level, heard, accuracy, focus, at) values (?, 'Very good', 'NAILED', true, 90, '[]'::jsonb, now())")
                .param(id).update();
        jdbc.sql("insert into usage (learner_id, session_id, vendor, kind, units, unit_type, at) values (?, ?, 'azure', 'stt', 1000, 'audio_ms', now())")
                .params(id, UUID.fromString(talk)).update();
        client.send("POST", "/api/events", cookie, "{\"name\":\"debrief_viewed\",\"props\":{\"fixes_count\":1}}");
        audio.put("talk/" + talk + "/clip-1.wav", new byte[]{1, 2, 3});
        audio.put("learner/" + id + "/day1.webm", new byte[]{4, 5, 6});
        return new Person(cookie, id, talk);
    }

    int rows(String table, UUID learner) {
        return jdbc.sql("select count(*) from " + table + " where learner_id = ?").param(learner).query(Integer.class).single();
    }

    @Test
    void everythingOfTheLearnerIsGone() throws Exception {
        Person p = person();
        var r = client.send("POST", "/api/me/delete", p.cookie(), "{\"confirm\":\"DELETE\"}");
        assertThat(r.status()).isEqualTo(204);
        for (String t : new String[]{"learner_profile", "learner_settings", "consent_record", "talk_session", "attempt", "event", "usage", "account", "web_session"}) {
            assertThat(rows(t, p.id())).as(t).isZero();
        }
        assertThat(jdbc.sql("select count(*) from debrief where session_id = ?").param(UUID.fromString(p.talk())).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from learner where id = ?").param(p.id()).query(Integer.class).single()).isZero();
        assertThat(audio.get("talk/" + p.talk() + "/clip-1.wav")).isEmpty();
        assertThat(audio.get("learner/" + p.id() + "/day1.webm")).isEmpty();
    }

    @Test
    void anotherLearnerIsUntouched() throws Exception {
        Person keep = person();
        Person gone = person();
        client.send("POST", "/api/me/delete", gone.cookie(), "{\"confirm\":\"DELETE\"}");
        assertThat(rows("talk_session", keep.id())).isEqualTo(1);
        assertThat(audio.get("talk/" + keep.talk() + "/clip-1.wav")).isPresent();
        assertThat(audio.get("learner/" + keep.id() + "/day1.webm")).isPresent();
    }

    @Test
    void aWrongConfirmDeletesNothing() throws Exception {
        Person p = person();
        for (String body : new String[]{"{\"confirm\":\"delete\"}", "{\"confirm\":\"DELETE \"}", "{}"}) {
            var r = client.send("POST", "/api/me/delete", p.cookie(), body);
            assertThat(r.status()).as(body).isEqualTo(422);
        }
        assertThat(rows("talk_session", p.id())).isEqualTo(1);
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("POST", "/api/me/delete", null, "{\"confirm\":\"DELETE\"}").status()).isEqualTo(401);
    }

    @Test
    void theOldCookieIsSignedOutAfterwards() throws Exception {
        Person p = person();
        client.send("POST", "/api/me/delete", p.cookie(), "{\"confirm\":\"DELETE\"}");
        assertThat(client.send("GET", "/api/me", p.cookie(), "").status()).isEqualTo(401);
    }
}
