package app.bolo.reminder;

import app.bolo.PostgresTestBase;
import app.bolo.reminder.adapter.out.push.FakePushAdapter;
import app.bolo.reminder.application.port.out.PushPort;
import app.bolo.reminder.application.service.ReminderSendService;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-5 / PRG-6: the daily reminder reaches every browser once, with the right line. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.admin.emails=boss@bolo.test")
class ReminderSendAcceptanceTest extends PostgresTestBase {

    static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ReminderSendService sender;
    @Autowired PushPort push;
    TalkTestClient client;
    String me;
    UUID learner;
    Instant now;

    FakePushAdapter fake() {
        return (FakePushAdapter) push;
    }

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        fake().clear();
        me = account("r" + UUID.randomUUID().toString().substring(0, 8) + "@x.com");
        learner = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", me, "").body(), "$.learnerId"));
        now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String time = LocalTime.ofInstant(now, DHAKA).toString();
        assertThat(client.send("PUT", "/api/reminder", me, "{\"on\":true,\"time\":\"" + time + "\",\"timeZone\":\"Asia/Dhaka\"}").status()).isEqualTo(200);
        // Only this test's learner is due: everyone else's reminder is pushed out of this window.
        jdbc.sql("update reminder_settings set enabled = false where learner_id <> ?").param(learner).update();
    }

    String account(String email) throws Exception {
        return client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
    }

    String device(String cookie) throws Exception {
        String endpoint = "https://push.example/" + UUID.randomUUID();
        client.send("POST", "/api/reminder/subscriptions", cookie, "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}");
        return endpoint;
    }

    List<String> bodies() {
        return fake().sent().stream().map(s -> (String) JsonPath.read(s.json(), "$.body")).toList();
    }

    @Test
    void sendsAtTimeToEveryDevice() throws Exception {
        String a = device(me), b = device(me);
        assertThat(sender.sendDue(now)).isEqualTo(1);
        assertThat(fake().sent()).extracting(s -> s.subscription().endpoint()).containsExactlyInAnyOrder(a, b);
        String json = fake().sent().getFirst().json();
        assertThat((String) JsonPath.read(json, "$.title")).isEqualTo("Bolo");
        assertThat((String) JsonPath.read(json, "$.url")).startsWith("/?from=reminder&r=");
        assertThat((String) JsonPath.read(json, "$.tag")).isEqualTo("bolo-daily");
        assertThat(sender.sendDue(now.plusSeconds(60))).isZero();
        assertThat(fake().sent()).hasSize(2);
    }

    @Test
    void goneDeviceIsRemovedOthersStillGetIt() throws Exception {
        String a = device(me), b = device(me);
        fake().gone(a);
        assertThat(sender.sendDue(now)).isEqualTo(1);
        assertThat(fake().sent()).extracting(s -> s.subscription().endpoint()).containsExactly(b);
        assertThat(jdbc.sql("select count(*) from push_subscription where endpoint = ?").param(a).query(Integer.class).single()).isZero();
    }

    @Test
    void talkedTodayNothing() throws Exception {
        device(me);
        jdbc.sql("insert into talk_session (id, learner_id, minutes, started_at, ended_at, end_reason) values (?, ?, 5, ?, ?, 'time_up')")
                .params(UUID.randomUUID(), learner, java.sql.Timestamp.from(now.minusSeconds(60)), java.sql.Timestamp.from(now)).update();
        assertThat(sender.sendDue(now)).isZero();
        assertThat(fake().sent()).isEmpty();
    }

    @Test
    void noFreeTalkLeftNothing() throws Exception {
        device(me);
        // The first week's 7 talks used up (5-minute talks two days ago, so not "today").
        for (int i = 0; i < 7; i++) {
            jdbc.sql("insert into talk_session (id, learner_id, minutes, started_at, ended_at, end_reason, attached_at) values (?, ?, 5, ?, ?, 'time_up', ?)")
                    .params(UUID.randomUUID(), learner, java.sql.Timestamp.from(now.minus(2, ChronoUnit.DAYS)),
                            java.sql.Timestamp.from(now.minus(2, ChronoUnit.DAYS).plus(5, ChronoUnit.MINUTES)),
                            java.sql.Timestamp.from(now.minus(2, ChronoUnit.DAYS))).update();
        }
        assertThat(sender.sendDue(now)).isZero();
    }

    @Test
    void concurrentPassesSendOnce() throws Exception {
        device(me);
        var x = CompletableFuture.supplyAsync(() -> sender.sendDue(now));
        var y = CompletableFuture.supplyAsync(() -> sender.sendDue(now));
        assertThat(x.get() + y.get()).isEqualTo(1);
        assertThat(fake().sent()).hasSize(1);
    }

    @Test
    void staleStoryLineFallsBackToPlain() throws Exception {
        device(me);
        jdbc.sql("update reminder_settings set line = 'Bolo wants to know how the demo went.', line_source = 'the demo on Friday' where learner_id = ?")
                .param(learner).update();
        // No notes match "the demo on Friday" (remembering off, or the line removed).
        sender.sendDue(now);
        assertThat(bodies()).hasSize(1);
        assertThat(bodies().getFirst()).isNotEqualTo("Bolo wants to know how the demo went.");
    }

    @Test
    void privateUsesPrivateLine() throws Exception {
        device(me);
        client.send("PUT", "/api/reminder", me, "{\"keepPrivate\":true}");
        jdbc.sql("update reminder_settings set line = 'Bolo wants to know how the demo went.', line_source = 'x' where learner_id = ?").param(learner).update();
        sender.sendDue(now);
        assertThat(bodies()).containsExactly("Time for today's talk.");
    }

    @Test
    void sentAndOpenedEventsRecorded() throws Exception {
        device(me);
        sender.sendDue(now);
        var props = jdbc.sql("select props::text from event where learner_id = ? and name = 'reminder_sent'").param(learner).query(String.class).single();
        assertThat(props).contains("\"slot\"").contains("\"hook_type\": \"plain\"");
        String url = JsonPath.read(fake().sent().getFirst().json(), "$.url");
        String id = url.substring(url.indexOf("r=") + 2);
        client.send("POST", "/api/reminder/opened", me, "{\"timeZone\":\"Asia/Dhaka\",\"reminder\":\"" + id + "\"}");
        assertThat(jdbc.sql("select count(*) from event where learner_id = ? and name = 'reminder_opened'").param(learner).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void adminTestReminderReachesTheAdminsDevices() throws Exception {
        String boss = account("boss@bolo.test");
        device(boss);
        var r = client.send("POST", "/api/admin/reminder-test", boss, "");
        assertThat(r.status()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(r.body(), "$.devices")).isEqualTo(1);
        assertThat(client.send("POST", "/api/admin/reminder-test", me, "").status()).isEqualTo(404);
    }
}
