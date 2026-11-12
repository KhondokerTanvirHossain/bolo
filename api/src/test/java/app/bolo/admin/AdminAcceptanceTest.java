package app.bolo.admin;

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

/** P2a: the founder's admin portal API — only for listed admin accounts; everyone else gets 404. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.admin.emails=boss@bolo.test, other@bolo.test",
        "bolo.talk.max-live-sessions=50"})
class AdminAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    String account(String email) throws Exception {
        String guest = client.consentedGuest(5);
        return client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
    }

    String id(String cookie) throws Exception {
        return JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId");
    }

    @Test
    void aNonAdminGets404() throws Exception {
        String me = account("someone@x.com");
        assertThat(client.send("GET", "/api/admin/users?q=", me, "").status()).isEqualTo(404);
        assertThat(client.send("GET", "/api/admin/usage", me, "").status()).isEqualTo(404);
        assertThat(client.send("PUT", "/api/admin/settings", me, "{\"dailyTalkCap\":1,\"plans\":[]}").status()).isEqualTo(404);
        assertThat(client.send("GET", "/api/admin/users?q=", client.consentedGuest(5), "").status()).isEqualTo(404);
    }

    @Test
    void adminFindsUsersByEmail() throws Exception {
        String boss = account("boss@bolo.test");
        String email = "friend" + UUID.randomUUID().toString().substring(0, 6) + "@x.com";
        account(email);
        var r = client.send("GET", "/api/admin/users?q=" + email, boss, "");
        assertThat(r.status()).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(r.body(), "$.users[*].email")).containsExactly(email);
        assertThat((String) JsonPath.read(r.body(), "$.users[0].plan")).isEqualTo("free");
    }

    @Test
    void adminGivesAFriendPremium() throws Exception {
        String boss = account("boss@bolo.test");
        String friend = account("pal" + UUID.randomUUID().toString().substring(0, 6) + "@x.com");
        var r = client.send("POST", "/api/admin/users/" + id(friend) + "/grant", boss, "{\"plan\":\"premium\",\"days\":30,\"note\":\"friend\"}");
        assertThat(r.status()).isEqualTo(204);
        assertThat((String) JsonPath.read(client.send("GET", "/api/home", friend, "").body(), "$.freeTalks.plan")).isEqualTo("premium");
        assertThat(client.send("POST", "/api/admin/users/" + id(friend) + "/grant", boss, "{\"plan\":\"gold\",\"days\":30}").status()).isEqualTo(422);
    }

    @Test
    void adminTurnsUnlimitedOnAndOff() throws Exception {
        String boss = account("other@bolo.test");
        String friend = account("u" + UUID.randomUUID().toString().substring(0, 6) + "@x.com");
        assertThat(client.send("POST", "/api/admin/users/" + id(friend) + "/unlimited", boss, "{\"on\":true}").status()).isEqualTo(204);
        assertThat((Boolean) JsonPath.read(client.send("GET", "/api/me", friend, "").body(), "$.unlimited")).isTrue();
        client.send("POST", "/api/admin/users/" + id(friend) + "/unlimited", boss, "{\"on\":false}");
        assertThat((Boolean) JsonPath.read(client.send("GET", "/api/me", friend, "").body(), "$.unlimited")).isFalse();
    }

    @Test
    void usageCountsTodaysTalks() throws Exception {
        String boss = account("boss@bolo.test");
        String u = account("t" + UUID.randomUUID().toString().substring(0, 6) + "@x.com");
        int before = JsonPath.read(client.send("GET", "/api/admin/usage?days=7", boss, "").body(), "$.now.today");
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        assertThat(client.startTalk(u).status()).isEqualTo(201);
        String usage = client.send("GET", "/api/admin/usage?days=7", boss, "").body();
        assertThat((Integer) JsonPath.read(usage, "$.now.today")).isEqualTo(before + 1);
        assertThat((List<?>) JsonPath.read(usage, "$.days")).hasSize(7);
        assertThat((Integer) JsonPath.read(usage, "$.now.cap")).isPositive();
    }

    @Test
    void settingsChangeTheCapAndPlans() throws Exception {
        String boss = account("boss@bolo.test");
        String before = client.send("GET", "/api/admin/settings", boss, "").body();
        try {
            var r = client.send("PUT", "/api/admin/settings", boss,
                    "{\"dailyTalkCap\":123,\"plans\":[{\"plan\":\"premium\",\"monthlyTalks\":25,\"priceBdt\":550,\"priceUsd\":6}]}");
            assertThat(r.status()).isEqualTo(204);
            String after = client.send("GET", "/api/admin/settings", boss, "").body();
            assertThat((Integer) JsonPath.read(after, "$.dailyTalkCap")).isEqualTo(123);
            assertThat((List<Integer>) JsonPath.read(after, "$.plans[?(@.plan=='premium')].monthlyTalks")).containsExactly(25);
        } finally {
            client.send("PUT", "/api/admin/settings", boss, "{\"dailyTalkCap\":" + JsonPath.read(before, "$.dailyTalkCap")
                    + ",\"plans\":[{\"plan\":\"premium\",\"monthlyTalks\":50,\"priceBdt\":500,\"priceUsd\":5}]}");
        }
    }

    /** Review: a settings save is all or nothing; a bad plan or a missing cap changes nothing. */
    @Test
    void aBadSettingsSaveChangesNothing() throws Exception {
        String boss = account("boss@bolo.test");
        String before = client.send("GET", "/api/admin/settings", boss, "").body();
        int cap = JsonPath.read(before, "$.dailyTalkCap");
        assertThat(client.send("PUT", "/api/admin/settings", boss,
                "{\"dailyTalkCap\":" + (cap + 7) + ",\"plans\":[{\"plan\":\"basic\",\"monthlyTalks\":9,\"priceBdt\":300,\"priceUsd\":3},"
                        + "{\"plan\":\"premium\",\"monthlyTalks\":-1,\"priceBdt\":500,\"priceUsd\":5}]}").status()).isEqualTo(422);
        assertThat(client.send("PUT", "/api/admin/settings", boss, "{\"plans\":[]}").status()).isEqualTo(422);
        assertThat(client.send("PUT", "/api/admin/settings", boss,
                "{\"dailyTalkCap\":" + cap + ",\"plans\":[{\"plan\":\"gold\",\"monthlyTalks\":9,\"priceBdt\":1,\"priceUsd\":1}]}").status()).isEqualTo(422);
        assertThat(client.send("PUT", "/api/admin/settings", boss,
                "{\"dailyTalkCap\":" + cap + ",\"plans\":[{\"plan\":\"basic\",\"monthlyTalks\":9,\"priceBdt\":1}]}").status()).isEqualTo(422);
        String after = client.send("GET", "/api/admin/settings", boss, "").body();
        assertThat((Integer) JsonPath.read(after, "$.dailyTalkCap")).isEqualTo(cap);
        assertThat((List<Integer>) JsonPath.read(after, "$.plans[?(@.plan=='basic')].monthlyTalks")).containsExactly(30);
    }
}
