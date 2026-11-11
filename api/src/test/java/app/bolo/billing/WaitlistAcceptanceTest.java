package app.bolo.billing;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P2c: the public plans and the waitlist (card payments abroad, bKash soon). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.admin.emails=boss@bolo.test")
class WaitlistAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    record Account(String cookie, String email) {
    }

    Account account() throws Exception {
        String email = "w" + UUID.randomUUID().toString().substring(0, 8) + "@x.com";
        return new Account(account(email), email);
    }

    String account(String email) throws Exception {
        return client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
    }

    static String join(String plan, String region) {
        return "{\"plan\":\"" + plan + "\",\"region\":\"" + region + "\",\"timeZone\":\"Europe/London\"}";
    }

    int rows(String email) {
        return jdbc.sql("select count(*) from waitlist w join account a on a.learner_id = w.learner_id where a.email = ?")
                .param(email).query(Integer.class).single();
    }

    @Test
    void plansArePublic() throws Exception {
        var r = client.send("GET", "/api/plans", null, "");
        assertThat(r.status()).isEqualTo(200);
        List<String> plans = JsonPath.read(r.body(), "$.plans[*].plan");
        assertThat(plans).containsExactly("basic", "premium", "ultimate");
        assertThat((Integer) JsonPath.read(r.body(), "$.plans[0].priceBdt")).isEqualTo(300);
        assertThat(((Number) JsonPath.read(r.body(), "$.plans[0].priceUsd")).doubleValue()).isEqualTo(3.0);
    }

    @Test
    void anAccountJoinsAndChangesPlan() throws Exception {
        var me = account();
        assertThat(client.send("GET", "/api/waitlist/me", me.cookie(), "").status()).isEqualTo(204);
        assertThat(client.send("POST", "/api/waitlist", me.cookie(), join("basic", "intl")).status()).isEqualTo(204);
        assertThat(client.send("POST", "/api/waitlist", me.cookie(), join("premium", "intl")).status()).isEqualTo(204);
        assertThat(rows(me.email())).isEqualTo(1);
        var mine = client.send("GET", "/api/waitlist/me", me.cookie(), "");
        assertThat((String) JsonPath.read(mine.body(), "$.plan")).isEqualTo("premium");
        assertThat((String) JsonPath.read(mine.body(), "$.region")).isEqualTo("intl");
    }

    @Test
    void aGuestIsAskedToSignIn() throws Exception {
        var r = client.send("POST", "/api/waitlist", client.consentedGuest(5), join("basic", "bd"));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("sign-in");
        assertThat(client.send("POST", "/api/waitlist", null, join("basic", "bd")).status()).isEqualTo(401);
    }

    @Test
    void badPlanOrRegionIs422() throws Exception {
        String me = account().cookie();
        assertThat(client.send("POST", "/api/waitlist", me, join("gold", "bd")).status()).isEqualTo(422);
        assertThat(client.send("POST", "/api/waitlist", me, join("basic", "mars")).status()).isEqualTo(422);
        assertThat(client.send("POST", "/api/waitlist", me,
                "{\"plan\":\"basic\",\"region\":\"bd\",\"timeZone\":\"" + "x".repeat(65) + "\"}").status()).isEqualTo(422);
    }

    @Test
    void deleteEverythingRemovesTheRow() throws Exception {
        var me = account();
        client.send("POST", "/api/waitlist", me.cookie(), join("basic", "bd"));
        assertThat(rows(me.email())).isEqualTo(1);
        long before = jdbc.sql("select count(*) from waitlist").query(Long.class).single();
        assertThat(client.send("POST", "/api/me/delete", me.cookie(), "{\"confirm\":\"DELETE\"}").status()).isEqualTo(204);
        assertThat(jdbc.sql("select count(*) from waitlist").query(Long.class).single()).isEqualTo(before - 1);
    }

    @Test
    void onlyTheAdminSeesTheWaitlist() throws Exception {
        var me = account();
        client.send("POST", "/api/waitlist", me.cookie(), join("ultimate", "bd"));
        assertThat(client.send("GET", "/api/admin/waitlist", me.cookie(), "").status()).isEqualTo(404);
        var r = client.send("GET", "/api/admin/waitlist", account("boss@bolo.test"), "");
        assertThat(r.status()).isEqualTo(200);
        List<String> emails = JsonPath.read(r.body(), "$.entries[*].email");
        int i = emails.indexOf(me.email());
        assertThat(i).isGreaterThanOrEqualTo(0);
        assertThat((String) JsonPath.read(r.body(), "$.entries[" + i + "].plan")).isEqualTo("ultimate");
        List<Integer> bd = JsonPath.read(r.body(), "$.counts[?(@.plan == 'ultimate')].bd");
        assertThat(bd.get(0)).isGreaterThanOrEqualTo(1);
    }
}
