package app.bolo.memory;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import app.bolo.memory.application.port.out.MemoryPort;
import app.bolo.memory.domain.Line;
import app.bolo.memory.domain.MemoryNote;
import app.bolo.memory.domain.MemoryThread;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P3b (ACC-9, 2026-11-13): Me → What Bolo remembers: see, remove, switch off, forget. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemoryApiAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired MemoryPort memory;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    LearnerId learnerOf(String cookie) throws Exception {
        return new LearnerId(UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId")));
    }

    static MemoryNote sample() {
        return new MemoryNote(List.of(new Line("a1", "Works as a software tester in Dhaka")),
                List.of(new MemoryThread("t1", MemoryThread.Kind.WORRY, "Nervous about the Monday meeting", LocalDate.of(2026, 11, 10), null, null, null)),
                List.of(new Line("i1", "Cricket")), List.of("Secret recent topic"));
    }

    String withNote() throws Exception {
        String cookie = client.consentedGuest(5);
        memory.save(learnerOf(cookie), true, sample(), 0, null);
        return cookie;
    }

    @Test
    void aNewLearnerSeesAnEmptyNoteWithRememberingOn() throws Exception {
        var r = client.send("GET", "/api/memory", client.consentedGuest(5), "");
        assertThat(r.status()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(r.body(), "$.enabled")).isTrue();
        assertThat((List<?>) JsonPath.read(r.body(), "$.followUps")).isEmpty();
        assertThat((List<?>) JsonPath.read(r.body(), "$.about")).isEmpty();
        assertThat((List<?>) JsonPath.read(r.body(), "$.interests")).isEmpty();
    }

    @Test
    void theViewShowsFollowUpsAboutAndInterestsButNotRecentTopics() throws Exception {
        String body = client.send("GET", "/api/memory", withNote(), "").body();
        assertThat((String) JsonPath.read(body, "$.followUps[0].id")).isEqualTo("t1");
        assertThat((String) JsonPath.read(body, "$.followUps[0].kind")).isEqualTo("worry");
        assertThat((String) JsonPath.read(body, "$.followUps[0].text")).isEqualTo("Nervous about the Monday meeting");
        assertThat((String) JsonPath.read(body, "$.followUps[0].said")).isEqualTo("2026-11-10");
        assertThat((String) JsonPath.read(body, "$.about[0].text")).isEqualTo("Works as a software tester in Dhaka");
        assertThat((String) JsonPath.read(body, "$.interests[0].text")).isEqualTo("Cricket");
        assertThat(body).doesNotContain("Secret recent topic").doesNotContain("openedTalk");
    }

    @Test
    void removingALineRemovesIt() throws Exception {
        String cookie = withNote();
        assertThat(client.send("DELETE", "/api/memory/lines/t1", cookie, "").status()).isEqualTo(204);
        assertThat(client.send("DELETE", "/api/memory/lines/t1", cookie, "").status()).isEqualTo(204);
        assertThat(memory.find(learnerOf(cookie)).orElseThrow().note().ids()).containsExactlyInAnyOrder("a1", "i1");
    }

    @Test
    void turningOffEmptiesTheNote() throws Exception {
        String cookie = withNote();
        assertThat(client.send("PUT", "/api/memory", cookie, "{\"enabled\":false}").status()).isEqualTo(204);
        var row = memory.find(learnerOf(cookie)).orElseThrow();
        assertThat(row.enabled()).isFalse();
        assertThat(row.note().isEmpty()).isTrue();
        assertThat((Boolean) JsonPath.read(client.send("GET", "/api/memory", cookie, "").body(), "$.enabled")).isFalse();
        client.send("PUT", "/api/memory", cookie, "{\"enabled\":true}");
        assertThat(memory.find(learnerOf(cookie)).orElseThrow().enabled()).isTrue();
    }

    @Test
    void aMissingEnabledIs422() throws Exception {
        assertThat(client.send("PUT", "/api/memory", withNote(), "{}").status()).isEqualTo(422);
    }

    @Test
    void forgetKeepsRememberingOn() throws Exception {
        String cookie = withNote();
        assertThat(client.send("POST", "/api/memory/forget", cookie, "").status()).isEqualTo(204);
        var row = memory.find(learnerOf(cookie)).orElseThrow();
        assertThat(row.enabled()).isTrue();
        assertThat(row.note().isEmpty()).isTrue();
        assertThat(row.note().recent()).isEmpty();
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("GET", "/api/memory", null, "").status()).isEqualTo(401);
        assertThat(client.send("DELETE", "/api/memory/lines/x", null, "").status()).isEqualTo(401);
        assertThat(client.send("PUT", "/api/memory", null, "{\"enabled\":false}").status()).isEqualTo(401);
        assertThat(client.send("POST", "/api/memory/forget", null, "").status()).isEqualTo(401);
    }

    @Test
    void deleteEverythingRemovesTheNote() throws Exception {
        String cookie = withNote();
        LearnerId learner = learnerOf(cookie);
        assertThat(client.send("POST", "/api/me/delete", cookie, "{\"confirm\":\"DELETE\"}").status()).isEqualTo(204);
        assertThat(memory.find(learner)).isEmpty();
    }

    @Test
    void aGuestsNoteMovesWhenSigningIntoAnExistingAccount() throws Exception {
        String uid = UUID.randomUUID().toString();
        String email = "mem" + uid.substring(0, 6) + "@x.com";
        String account = client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + uid + ":" + email + "\"}").cookie();
        LearnerId accountId = learnerOf(account);
        memory.save(accountId, true, new MemoryNote(List.of(new Line("acc", "Lives in Mirpur")), List.of(), List.of(), List.of()), 2, null);
        String guest = withNote();
        assertThat(client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"fake:" + uid + ":" + email + "\"}").status()).isEqualTo(200);
        assertThat(memory.find(accountId).orElseThrow().note().ids()).contains("acc", "a1", "t1", "i1");
    }
}
