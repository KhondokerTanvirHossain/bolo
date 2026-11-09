package app.bolo.talk;

import app.bolo.PostgresTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P1d: sign in before the first talk (replaces ACC-2's 3 guest talks); the free plan, ACC-5 / US-8.7. */
class FreePlanAcceptanceTest {

    /** Starts and finishes n talks (attached and ended); returns their ids. */
    static List<UUID> talkTimes(TalkTestClient client, JdbcClient jdbc, String cookie, int n) throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            var r = client.startTalk(cookie);
            assertThat(r.status()).as("talk %d: %s", i + 1, r.body()).isEqualTo(201);
            UUID id = UUID.fromString(TalkTestClient.id(r));
            jdbc.sql("update talk_session set attached_at = now() - interval '5 minutes', ended_at = now(), end_reason = 'learner_ended' where id = ?")
                    .param(id).update();
            ids.add(id);
        }
        return ids;
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.talk.max-live-sessions=50",
            "bolo.firebase.api-key=k", "bolo.firebase.auth-domain=d", "bolo.firebase.project-id=p", "bolo.firebase.app-id=a"})
    class SignInOn extends PostgresTestBase {
        @LocalServerPort int port;
        @Autowired JdbcClient jdbc;
        TalkTestClient client;

        @BeforeEach
        void setUp() {
            jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
            client = new TalkTestClient(port);
        }

        String account(int talkMinutes) throws Exception {
            String guest = client.consentedGuest(talkMinutes);
            String cookie = client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"fake:fp" + UUID.randomUUID() + ":a@x.com\"}").cookie();
            assertThat((String) JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.kind")).isEqualTo("account");
            return cookie;
        }

        @Test
        void aGuestMustSignInBeforeTheFirstTalk() throws Exception {
            var r = client.startTalk(client.consentedGuest(5));
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.body()).contains("sign-in");
        }

        @Test
        void anUnlimitedGuestMayTalk() throws Exception {
            String code = "unl" + UUID.randomUUID().toString().substring(0, 8);
            jdbc.sql("insert into invite (code, label, max_uses, created_at, unlimited) values (?, 'test', 10, now(), true)").param(code).update();
            String cookie = client.send("POST", "/api/session", null, "{\"invite\":\"" + code + "\"}").cookie();
            client.send("POST", "/api/consent", cookie, "{\"keepRecordings\":true,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");
            assertThat(client.startTalk(cookie).status()).isEqualTo(201);
        }

        @Test
        void theEighthTalkInTheFirstWeekIsRefused() throws Exception {
            String me = account(5);
            talkTimes(client, jdbc, me, 7);
            var r = client.startTalk(me);
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.body()).contains("free-limit");
        }

        @Test
        void aShortTooShortTalkAndAFailedOneDoNotCount() throws Exception {
            String me = account(5);
            List<UUID> ids = talkTimes(client, jdbc, me, 7);
            jdbc.sql("insert into debrief (session_id, status, updated_at) values (?, 'too_short', now()), (?, 'failed', now()), (?, 'ready', now())")
                    .params(ids.get(0), ids.get(1), ids.get(2)).update();
            jdbc.sql("update talk_session set attached_at = now() - interval '40 seconds' where id = ?").param(ids.get(0)).update();
            assertThat(client.startTalk(me).status()).isEqualTo(201);
        }

        int left(String cookie) throws Exception {
            return JsonPath.read(client.send("GET", "/api/home", cookie, "").body(), "$.freeTalks.left");
        }

        /** Review: a talk that ran its course counts even when the learner said too little for feedback (Bolo still ran). */
        @Test
        void aLongTooShortTalkCounts() throws Exception {
            String me = account(5);
            UUID id = talkTimes(client, jdbc, me, 1).getFirst();
            jdbc.sql("insert into debrief (session_id, status, updated_at) values (?, 'too_short', now())").param(id).update();
            assertThat(left(me)).isEqualTo(6);
        }

        /** Review: a talk that dropped within a minute with no reply (no debrief at all) isn't the learner's free talk. */
        @Test
        void aQuickDropWithNoFeedbackDoesNotCount() throws Exception {
            String me = account(5);
            UUID id = talkTimes(client, jdbc, me, 1).getFirst();
            jdbc.sql("update talk_session set attached_at = now() - interval '20 seconds' where id = ?").param(id).update();
            assertThat(left(me)).isEqualTo(7);
        }

        @Test
        void aTalkStillOpenCounts() throws Exception {
            String me = account(5);
            talkTimes(client, jdbc, me, 6);
            UUID open = UUID.fromString(TalkTestClient.id(client.startTalk(me)));
            jdbc.sql("update talk_session set attached_at = now() where id = ?").param(open).update();
            assertThat(client.startTalk(me).status()).isEqualTo(422);
        }

        /** Review: talk starts fired at once can't each pass the check; at most one new talk stays open to attach. */
        @Test
        void startsFiredAtOnceLeaveOneOpenTalk() throws Exception {
            String me = account(5);
            talkTimes(client, jdbc, me, 6);
            UUID learner = UUID.fromString(JsonPath.read(client.send("GET", "/api/me", me, "").body(), "$.learnerId"));
            var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
            var go = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<Integer>> all = new ArrayList<>();
            for (int i = 0; i < 8; i++) all.add(pool.submit(() -> { go.await(); return new TalkTestClient(port).startTalk(me).status(); }));
            go.countDown();
            for (var f : all) f.get();
            pool.shutdown();
            int open = jdbc.sql("select count(*) from talk_session where learner_id = ? and ended_at is null").param(learner)
                    .query(Integer.class).single();
            assertThat(open).isEqualTo(1);
        }

        @Autowired app.bolo.talk.application.port.in.AudioRetentionUseCase retention;

        String signIn(String uid) throws Exception {
            String guest = client.consentedGuest(5);
            return client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"fake:" + uid + ":a@x.com\"}").cookie();
        }

        /** Task 6 (founder): deleting everything and signing in again doesn't hand out a new free week. */
        @Test
        void deletingAndSigningInAgainKeepsTheFreeTalksUsed() throws Exception {
            String uid = "del" + UUID.randomUUID();
            String me = signIn(uid);
            talkTimes(client, jdbc, me, 7);
            assertThat(client.send("POST", "/api/me/delete", me, "{\"confirm\":\"DELETE\"}").status()).isEqualTo(204);
            String again = signIn(uid);
            var r = client.startTalk(again);
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.body()).contains("free-limit");
            String rows = jdbc.sql("select string_agg(subject_hash, ',') from free_talk_use").query(String.class).single();
            assertThat(rows).doesNotContain(uid).doesNotContain("a@x.com");
        }

        @Test
        void anotherGoogleAccountIsUnaffected() throws Exception {
            String me = signIn("del" + UUID.randomUUID());
            talkTimes(client, jdbc, me, 7);
            client.send("POST", "/api/me/delete", me, "{\"confirm\":\"DELETE\"}");
            assertThat(client.startTalk(signIn("other" + UUID.randomUUID())).status()).isEqualTo(201);
        }

        @Test
        void rememberedUseOlderThan12MonthsIsForgotten() {
            jdbc.sql("insert into free_talk_use (subject_hash, started_at) values ('old-key', now() - interval '13 months'), ('new-key', now())").update();
            retention.sweep();
            assertThat(jdbc.sql("select count(*) from free_talk_use where subject_hash = 'old-key'").query(Integer.class).single()).isZero();
            assertThat(jdbc.sql("select count(*) from free_talk_use where subject_hash = 'new-key'").query(Integer.class).single()).isOne();
        }

        @Test
        void freeTalksAreFiveMinutes() throws Exception {
            var r = client.startTalk(account(10));
            assertThat(r.status()).isEqualTo(201);
            assertThat((Integer) JsonPath.read(r.body(), "$.minutes")).isEqualTo(5);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.talk.max-live-sessions=50"})
    class SignInOff extends PostgresTestBase {
        @LocalServerPort int port;
        @Autowired JdbcClient jdbc;

        @Test
        void withSignInOffGuestsAreNotLimited() throws Exception {
            jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
            TalkTestClient client = new TalkTestClient(port);
            String guest = client.consentedGuest(5);
            talkTimes(client, jdbc, guest, 8);
            assertThat(client.startTalk(guest).status()).isEqualTo(201);
        }
    }
}
