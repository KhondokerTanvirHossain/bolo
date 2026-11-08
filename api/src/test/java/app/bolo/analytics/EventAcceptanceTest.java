package app.bolo.analytics;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkSocket;
import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §10 analytics: allow-listed funnel events from the browser, and the talk's own start and end. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class EventAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;
    String cookie;
    String learnerId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        learnerId = com.jayway.jsonpath.JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId");
    }

    int count(String name) {
        return jdbc.sql("select count(*) from event where learner_id = ? and name = ?").params(UUID.fromString(learnerId), name)
                .query(Integer.class).single();
    }

    @Test
    void aFunnelEventIsStored() throws Exception {
        var r = client.send("POST", "/api/events", cookie, "{\"name\":\"first_word_spoken\",\"props\":{\"seconds_since_first_open\":37}}");
        assertThat(r.status()).isEqualTo(202);
        assertThat(jdbc.sql("select props->>'seconds_since_first_open' from event where learner_id = ? and name = 'first_word_spoken'")
                .param(UUID.fromString(learnerId)).query(String.class).single()).isEqualTo("37");
    }

    @Test
    void anUnknownEventIs422() throws Exception {
        assertThat(client.send("POST", "/api/events", cookie, "{\"name\":\"what_i_said\",\"props\":{}}").status()).isEqualTo(422);
        assertThat(client.send("POST", "/api/events", cookie, "{\"name\":\"onboarding_step_completed\",\"props\":{\"step\":\"I want to die.\"}}").status())
                .isEqualTo(422);
    }

    @Test
    void the61stEventInAMinuteIs429() throws Exception {
        for (int i = 0; i < 60; i++) {
            assertThat(client.send("POST", "/api/events", cookie, "{\"name\":\"app_first_open\",\"props\":{}}").status()).isEqualTo(202);
        }
        assertThat(client.send("POST", "/api/events", cookie, "{\"name\":\"app_first_open\",\"props\":{}}").status()).isEqualTo(429);
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("POST", "/api/events", null, "{\"name\":\"app_first_open\",\"props\":{}}").status()).isEqualTo(401);
    }

    @Test
    void aTalkRecordsSessionStartedAndEnded() throws Exception {
        String id = TalkTestClient.id(client.startTalk(cookie));
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
            s.json("{\"type\":\"end\"}");
            s.await(3000, "\"ended\"");
        }
        long until = System.currentTimeMillis() + 3000;
        while (count("session_ended") == 0 && System.currentTimeMillis() < until) Thread.sleep(50);
        assertThat(count("session_started")).isEqualTo(1);
        assertThat(jdbc.sql("select props->>'end_reason' from event where learner_id = ? and name = 'session_ended'")
                .param(UUID.fromString(learnerId)).query(String.class).single()).isEqualTo("learner_ended");
    }
}
