package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.application.port.in.TalkEnded;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** AX-7 (2026-11-14): after every talk Bolo picks the learner's automatic Time to answer from their start delays. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutoAnswerTimeAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ApplicationEventPublisher events;

    TalkTestClient client;
    String cookie;
    LearnerId learner;

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        learner = new LearnerId(UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId")));
    }

    /** A finished talk whose learner turns started after these delays. */
    TalkSessionId talkWith(int... startMs) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into talk_session (id, learner_id, minutes, started_at, ended_at, end_reason) values (?, ?, 5, now(), now(), 'time_up')")
                .params(id, learner.value()).update();
        for (int i = 0; i < startMs.length; i++) {
            jdbc.sql("insert into talk_turn (session_id, idx, speaker, text, at, start_ms) values (?, ?, 'learner', 'hello there', now(), ?)")
                    .params(id, i * 2 + 1, startMs[i]).update();
        }
        return new TalkSessionId(id);
    }

    String setting(String field) throws Exception {
        return JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.settings." + field);
    }

    @Test
    void aSlowStarterBecomesRelaxed() throws Exception {
        TalkSessionId talk = talkWith(12_000, 15_000, 9_000, 20_000, 11_000, 30_000);
        events.publishEvent(new TalkEnded(talk, learner, 6, "time_up"));
        assertThat(setting("answerTimeAuto")).isEqualTo("relaxed");
    }

    @Test
    void aQuickStarterBecomesQuick() throws Exception {
        TalkSessionId talk = talkWith(500, 800, 1_200, 400, 900);
        events.publishEvent(new TalkEnded(talk, learner, 5, "time_up"));
        assertThat(setting("answerTimeAuto")).isEqualTo("quick");
    }

    @Test
    void tooFewAnswersKeepNormal() throws Exception {
        TalkSessionId talk = talkWith(20_000, 20_000);
        events.publishEvent(new TalkEnded(talk, learner, 2, "time_up"));
        assertThat(setting("answerTimeAuto")).isEqualTo("normal");
    }

    /** Review focus 1: a speed the learner chose stays, however many talks Bolo measures. */
    @Test
    void theLearnersOwnChoiceIsKept() throws Exception {
        client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"answerTime\":\"quick\"}");
        for (int i = 0; i < 3; i++) {
            events.publishEvent(new TalkEnded(talkWith(12_000, 15_000, 9_000, 20_000, 11_000), learner, 5, "time_up"));
        }
        assertThat(setting("answerTime")).isEqualTo("quick");
        assertThat(setting("answerTimeAuto")).isEqualTo("relaxed");
    }

    @Test
    void onlyTheLatestThirtyAnswersCount() throws Exception {
        int[] old = new int[30];
        java.util.Arrays.fill(old, 20_000);
        talkWith(old);
        jdbc.sql("update talk_turn set at = now() - interval '1 day'").update();
        int[] recent = new int[30];
        java.util.Arrays.fill(recent, 1_000);
        TalkSessionId talk = talkWith(recent);
        events.publishEvent(new TalkEnded(talk, learner, 30, "time_up"));
        assertThat(setting("answerTimeAuto")).isEqualTo("quick");
    }
}
