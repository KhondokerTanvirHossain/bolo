package app.bolo.reminder;

import app.bolo.PostgresTestBase;
import app.bolo.reminder.application.service.ReminderSendService;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Review (reminders, I5): Web Push without VAPID keys is off — no key for browsers, nothing claimed, nothing counted. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.push.vendor=webpush")
class ReminderUnavailableAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ReminderSendService sender;

    @Test
    void withoutKeysRemindersAreOff() throws Exception {
        var client = new TalkTestClient(port);
        String me = client.send("POST", "/api/auth/google", client.consentedGuest(5),
                "{\"idToken\":\"fake:" + UUID.randomUUID() + ":u" + UUID.randomUUID().toString().substring(0, 6) + "@x.com\"}").cookie();
        UUID learner = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", me, "").body(), "$.learnerId"));
        String view = client.send("GET", "/api/reminder", me, "").body();
        assertThat((Object) JsonPath.read(view, "$.publicKey")).isNull();
        var sub = client.send("POST", "/api/reminder/subscriptions", me, "{\"endpoint\":\"https://push.example/x\",\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}");
        assertThat(sub.status()).isEqualTo(422);
        assertThat(sub.body()).contains("unavailable");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String time = LocalTime.ofInstant(now, ZoneId.of("Asia/Dhaka")).toString();
        client.send("PUT", "/api/reminder", me, "{\"on\":true,\"time\":\"" + time + "\",\"timeZone\":\"Asia/Dhaka\"}");
        jdbc.sql("insert into push_subscription (learner_id, endpoint, p256dh, auth, created_at) values (?, ?, 'k', 'a', now())")
                .params(learner, "https://push.example/" + UUID.randomUUID()).update();
        assertThat(sender.sendDue(now)).isZero();
        assertThat(jdbc.sql("select last_sent_on from reminder_settings where learner_id = ?").param(learner).query().singleRow().get("last_sent_on")).isNull();
    }
}
