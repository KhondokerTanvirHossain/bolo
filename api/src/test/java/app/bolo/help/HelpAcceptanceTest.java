package app.bolo.help;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P2b: a stuck learner reports a problem; it reaches the admin's Help inbox with its context. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.admin.emails=boss@bolo.test")
class HelpAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    String account(String email) throws Exception {
        return client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
    }

    static String report(String message) {
        return "{\"message\":\"" + message + "\",\"context\":{\"view\":\"home\",\"talkId\":\"t-1\",\"browser\":\"Firefox\",\"secret\":\"drop me\"}}";
    }

    @Test
    void aLearnerReportsAProblemAndTheAdminSeesIt() throws Exception {
        String email = "stuck" + UUID.randomUUID().toString().substring(0, 6) + "@x.com";
        String me = account(email);
        assertThat(client.send("POST", "/api/help", me, report("The mic button does nothing")).status()).isEqualTo(204);
        String boss = account("boss@bolo.test");
        String inbox = client.send("GET", "/api/admin/help", boss, "").body();
        List<String> emails = JsonPath.read(inbox, "$.requests[*].email");
        int i = emails.indexOf(email);
        assertThat(i).isGreaterThanOrEqualTo(0);
        assertThat((String) JsonPath.read(inbox, "$.requests[" + i + "].message")).isEqualTo("The mic button does nothing");
        assertThat((String) JsonPath.read(inbox, "$.requests[" + i + "].context.view")).isEqualTo("home");
    }

    @Test
    void onlyKnownContextIsKept() throws Exception {
        String email = "ctx" + UUID.randomUUID().toString().substring(0, 6) + "@x.com";
        String me = account(email);
        client.send("POST", "/api/help", me, report("hello"));
        String inbox = client.send("GET", "/api/admin/help", account("boss@bolo.test"), "").body();
        assertThat(inbox).doesNotContain("drop me");
    }

    @Test
    void aGuestCanReport() throws Exception {
        assertThat(client.send("POST", "/api/help", client.consentedGuest(5), report("stuck on the mic step")).status()).isEqualTo(204);
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("POST", "/api/help", null, report("x")).status()).isEqualTo(401);
    }

    @Test
    void emptyOrTooLongIs422() throws Exception {
        String me = client.consentedGuest(5);
        assertThat(client.send("POST", "/api/help", me, report("  ")).status()).isEqualTo(422);
        assertThat(client.send("POST", "/api/help", me, report("x".repeat(1001))).status()).isEqualTo(422);
    }

    @Test
    void sixInAnHourIs429() throws Exception {
        String me = client.consentedGuest(5);
        for (int i = 0; i < 5; i++) assertThat(client.send("POST", "/api/help", me, report("report " + i)).status()).isEqualTo(204);
        assertThat(client.send("POST", "/api/help", me, report("one more")).status()).isEqualTo(429);
    }

    @Test
    void onlyTheAdminSeesAndClosesReports() throws Exception {
        String me = account("n" + UUID.randomUUID().toString().substring(0, 6) + "@x.com");
        client.send("POST", "/api/help", me, report("close me"));
        assertThat(client.send("GET", "/api/admin/help", me, "").status()).isEqualTo(404);
        String boss = account("boss@bolo.test");
        String inbox = client.send("GET", "/api/admin/help", boss, "").body();
        List<String> messages = JsonPath.read(inbox, "$.requests[*].message");
        Object id = ((List<?>) JsonPath.read(inbox, "$.requests[*].id")).get(messages.indexOf("close me"));
        assertThat(client.send("POST", "/api/admin/help/" + id + "/done", me, "{}").status()).isEqualTo(404);
        assertThat(client.send("POST", "/api/admin/help/" + id + "/done", boss, "{}").status()).isEqualTo(204);
        assertThat((List<String>) JsonPath.read(client.send("GET", "/api/admin/help", boss, "").body(), "$.requests[*].message")).doesNotContain("close me");
    }
}
