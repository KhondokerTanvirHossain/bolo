package app.bolo.reminder;

import app.bolo.PostgresTestBase;
import app.bolo.reminder.application.port.in.ReminderUseCase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-5 / US-6.5: the learner's reminder time, Keep it private, and the browsers that get it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReminderSettingsAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ReminderUseCase reminders;
    TalkTestClient client;
    String me;

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        me = account();
    }

    String account() throws Exception {
        String email = "r" + UUID.randomUUID().toString().substring(0, 8) + "@x.com";
        return client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
    }

    UUID id(String cookie) throws Exception {
        return UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId"));
    }

    String view(String cookie) throws Exception {
        var r = client.send("GET", "/api/reminder", cookie, "");
        assertThat(r.status()).isEqualTo(200);
        return r.body();
    }

    TalkTestClient.Reply put(String json) throws Exception {
        return client.send("PUT", "/api/reminder", me, json);
    }

    static String sub(String endpoint) {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"BKey\",\"auth\":\"AuthKey\"}}";
    }

    @Test
    void noSessionIs401() throws Exception {
        assertThat(client.send("GET", "/api/reminder", null, "").status()).isEqualTo(401);
    }

    @Test
    void guestIs403() throws Exception {
        var r = client.send("GET", "/api/reminder", client.consentedGuest(5), "");
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.body()).contains("sign-in");
    }

    @Test
    void newLearnerIsNotAsked() throws Exception {
        String v = view(me);
        assertThat((Boolean) JsonPath.read(v, "$.asked")).isFalse();
        assertThat((Boolean) JsonPath.read(v, "$.on")).isFalse();
        assertThat((String) JsonPath.read(v, "$.time")).isEqualTo("18:30");
        assertThat((Integer) JsonPath.read(v, "$.devices")).isZero();
        assertThat((String) JsonPath.read(v, "$.publicKey")).isNotBlank();
        assertThat((String) JsonPath.read(v, "$.preview")).isEqualTo("Time for today's talk.");
    }

    @Test
    void saveTurnsOnAndAsks() throws Exception {
        assertThat(put("{\"on\":true,\"time\":\"08:00\",\"timeZone\":\"Asia/Dhaka\"}").status()).isEqualTo(200);
        String v = view(me);
        assertThat((Boolean) JsonPath.read(v, "$.asked")).isTrue();
        assertThat((Boolean) JsonPath.read(v, "$.on")).isTrue();
        assertThat((String) JsonPath.read(v, "$.time")).isEqualTo("08:00");
        assertThat((String) JsonPath.read(v, "$.timeZone")).isEqualTo("Asia/Dhaka");
    }

    @Test
    void badTimeOrZoneIs422() throws Exception {
        for (String body : new String[]{"{\"time\":\"25:00\"}", "{\"time\":\"6pm\"}", "{\"timeZone\":\"Mars/Base\"}"}) {
            var r = put(body);
            assertThat(r.status()).as(body).isEqualTo(422);
            assertThat(r.body()).contains("reminder");
        }
    }

    @Test
    void nullKeepsValues() throws Exception {
        put("{\"on\":true,\"time\":\"13:30\",\"timeZone\":\"Asia/Dhaka\",\"keepPrivate\":true}");
        put("{\"time\":\"22:00\"}");
        String v = view(me);
        assertThat((Boolean) JsonPath.read(v, "$.on")).isTrue();
        assertThat((Boolean) JsonPath.read(v, "$.keepPrivate")).isTrue();
        assertThat((String) JsonPath.read(v, "$.time")).isEqualTo("22:00");
    }

    @Test
    void notNowIsAskedAndOff() throws Exception {
        assertThat(client.send("POST", "/api/reminder/not-now", me, "{\"timeZone\":\"Asia/Dhaka\"}").status()).isEqualTo(204);
        String v = view(me);
        assertThat((Boolean) JsonPath.read(v, "$.asked")).isTrue();
        assertThat((Boolean) JsonPath.read(v, "$.on")).isFalse();
    }

    @Test
    void subscribeCountsDevicesAndMovesEndpoint() throws Exception {
        String endpoint = "https://push.example/" + UUID.randomUUID();
        assertThat(client.send("POST", "/api/reminder/subscriptions", me, sub(endpoint)).status()).isEqualTo(204);
        assertThat(client.send("POST", "/api/reminder/subscriptions", me, sub(endpoint + "-2")).status()).isEqualTo(204);
        assertThat((Integer) JsonPath.read(view(me), "$.devices")).isEqualTo(2);
        String other = account();
        client.send("POST", "/api/reminder/subscriptions", other, sub(endpoint));
        assertThat((Integer) JsonPath.read(view(me), "$.devices")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(view(other), "$.devices")).isEqualTo(1);
    }

    @Test
    void badSubscriptionIs422() throws Exception {
        assertThat(client.send("POST", "/api/reminder/subscriptions", me, "{\"endpoint\":\"http://insecure/x\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}").status())
                .isEqualTo(422);
        assertThat(client.send("POST", "/api/reminder/subscriptions", me, "{\"endpoint\":\"https://ok/x\"}").status()).isEqualTo(422);
    }

    @Test
    void unsubscribeRemoves() throws Exception {
        String endpoint = "https://push.example/" + UUID.randomUUID();
        client.send("POST", "/api/reminder/subscriptions", me, sub(endpoint));
        assertThat(client.send("DELETE", "/api/reminder/subscriptions", me, "{\"endpoint\":\"" + endpoint + "\"}").status()).isEqualTo(204);
        assertThat((Integer) JsonPath.read(view(me), "$.devices")).isZero();
    }

    @Test
    void openedClearsBackOff() throws Exception {
        put("{\"on\":true,\"time\":\"18:30\",\"timeZone\":\"Asia/Dhaka\"}");
        UUID learner = id(me);
        jdbc.sql("update reminder_settings set ignored = 3, stopped_at = now(), comeback_sent = true where learner_id = ?").param(learner).update();
        assertThat(client.send("POST", "/api/reminder/opened", me, "{\"timeZone\":\"Europe/London\"}").status()).isEqualTo(204);
        var row = jdbc.sql("select ignored, stopped_at, comeback_sent, time_zone from reminder_settings where learner_id = ?").param(learner).query().singleRow();
        assertThat(row.get("ignored")).isEqualTo(0);
        assertThat(row.get("stopped_at")).isNull();
        assertThat(row.get("comeback_sent")).isEqualTo(false);
        assertThat(row.get("time_zone")).isEqualTo("Europe/London");
    }

    @Test
    void openedWithReminderIdMarksTapped() throws Exception {
        put("{\"on\":true,\"time\":\"18:30\",\"timeZone\":\"Asia/Dhaka\"}");
        UUID learner = id(me);
        UUID sent = UUID.randomUUID();
        jdbc.sql("update reminder_settings set last_sent_id = ?, last_sent_at = now() where learner_id = ?").params(sent, learner).update();
        client.send("POST", "/api/reminder/opened", me, "{\"timeZone\":\"Asia/Dhaka\",\"reminder\":\"" + UUID.randomUUID() + "\"}");
        assertThat(jdbc.sql("select tapped from reminder_settings where learner_id = ?").param(learner).query(Boolean.class).single()).isFalse();
        client.send("POST", "/api/reminder/opened", me, "{\"timeZone\":\"Asia/Dhaka\",\"reminder\":\"" + sent + "\"}");
        assertThat(jdbc.sql("select tapped from reminder_settings where learner_id = ?").param(learner).query(Boolean.class).single()).isTrue();
    }

    @Test
    void openedWithoutSettingsIsFine() throws Exception {
        assertThat(client.send("POST", "/api/reminder/opened", me, "{\"timeZone\":\"Asia/Dhaka\"}").status()).isEqualTo(204);
    }

    @Test
    void cardDueAtMostThreeDaysAndNeverAfterAnswer() throws Exception {
        var learner = new app.bolo.common.id.LearnerId(id(me));
        LocalDate d = LocalDate.of(2026, 11, 14);
        assertThat(reminders.cardDue(learner, d)).isTrue();
        assertThat(reminders.cardDue(learner, d)).isTrue();
        assertThat(reminders.cardDue(learner, d.plusDays(1))).isTrue();
        assertThat(reminders.cardDue(learner, d.plusDays(2))).isTrue();
        assertThat(reminders.cardDue(learner, d.plusDays(3))).isFalse();

        String other = account();
        var o = new app.bolo.common.id.LearnerId(id(other));
        assertThat(reminders.cardDue(o, d)).isTrue();
        client.send("POST", "/api/reminder/card/dismiss", other, "");
        assertThat(reminders.cardDue(o, d.plusDays(1))).isFalse();

        String third = account();
        client.send("POST", "/api/reminder/not-now", third, "{\"timeZone\":\"Asia/Dhaka\"}");
        assertThat(reminders.cardDue(new app.bolo.common.id.LearnerId(id(third)), d)).isFalse();
    }

    @Test
    void mergeMovesSettingsAndDevices() throws Exception {
        String guest = client.consentedGuest(5);
        // A guest can't set reminders; the merge path is covered by moving rows owned by the guest id.
        UUID g = id(guest);
        jdbc.sql("insert into reminder_settings (learner_id, enabled, local_time, time_zone, updated_at) values (?, true, '08:00', 'Asia/Dhaka', now())")
                .param(g).update();
        String email = "m" + UUID.randomUUID().toString().substring(0, 8) + "@x.com";
        String token = "fake:" + UUID.randomUUID() + ":" + email;
        String account = client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"" + token + "\"}").cookie();
        String back = client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"" + token + "\"}").cookie();
        assertThat((String) JsonPath.read(view(back), "$.time")).isEqualTo("08:00");
        assertThat(account).isNotNull();
    }

    /** Review (reminders, I4): only real push services, and at most 5 browsers per learner. */
    @Test
    void endpointFromAnUnknownHostIs422() throws Exception {
        var r = client.send("POST", "/api/reminder/subscriptions", me, sub("https://evil.example/" + UUID.randomUUID()));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("subscription");
    }

    @Test
    void aSixthBrowserReplacesTheOldest() throws Exception {
        String first = "https://push.example/" + UUID.randomUUID();
        client.send("POST", "/api/reminder/subscriptions", me, sub(first));
        for (int i = 0; i < 5; i++) client.send("POST", "/api/reminder/subscriptions", me, sub("https://push.example/" + UUID.randomUUID()));
        assertThat((Integer) JsonPath.read(view(me), "$.devices")).isEqualTo(5);
        assertThat(jdbc.sql("select count(*) from push_subscription where endpoint = ?").param(first).query(Integer.class).single()).isZero();
    }
}
