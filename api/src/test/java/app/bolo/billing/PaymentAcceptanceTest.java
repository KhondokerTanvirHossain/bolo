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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** P2e: bKash Send Money to the founder's number, the learner types the TrxID, the admin approves (Option A). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.admin.emails=boss@bolo.test")
class PaymentAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;
    String boss;

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        boss = account("boss@bolo.test");
        bkash("01712345678");
    }

    String account(String email) throws Exception {
        return client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
    }

    String account() throws Exception {
        return account("p" + UUID.randomUUID().toString().substring(0, 8) + "@x.com");
    }

    void bkash(String number) throws Exception {
        assertThat(client.send("PUT", "/api/admin/settings", boss, "{\"dailyTalkCap\":300,\"bkashNumber\":\"" + number + "\"}").status()).isEqualTo(204);
    }

    static String trx() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
    }

    static String pay(String plan, String trx) {
        return "{\"plan\":\"" + plan + "\",\"trxId\":\"" + trx + "\",\"payerNumber\":\"01812345678\"}";
    }

    long pendingId(String trx) throws Exception {
        String list = client.send("GET", "/api/admin/payments", boss, "").body();
        List<Integer> ids = JsonPath.read(list, "$.payments[?(@.trxId == '" + trx + "')].id");
        return ids.get(0);
    }

    String plan(String cookie) throws Exception {
        return JsonPath.read(client.send("GET", "/api/home", cookie, "").body(), "$.freeTalks.plan");
    }

    @Test
    void theNumberIsPublicOnceSet() throws Exception {
        assertThat((String) JsonPath.read(client.send("GET", "/api/payments/bkash", null, "").body(), "$.number")).isEqualTo("01712345678");
    }

    @Test
    void aLearnerPaysAndTheAdminApproves() throws Exception {
        String me = account();
        String t = trx();
        assertThat(client.send("POST", "/api/payments", me, pay("basic", t.toLowerCase())).status()).isEqualTo(204);
        var mine = client.send("GET", "/api/payments/me", me, "").body();
        assertThat((String) JsonPath.read(mine, "$.status")).isEqualTo("pending");
        assertThat((String) JsonPath.read(mine, "$.trxId")).isEqualTo(t);
        String list = client.send("GET", "/api/admin/payments", boss, "").body();
        assertThat((List<Integer>) JsonPath.read(list, "$.payments[?(@.trxId == '" + t + "')].amountBdt")).containsExactly(300);
        assertThat(client.send("POST", "/api/admin/payments/" + pendingId(t) + "/approve", boss, "{}").status()).isEqualTo(204);
        assertThat(plan(me)).isEqualTo("basic");
        assertThat((String) JsonPath.read(client.send("GET", "/api/payments/me", me, "").body(), "$.status")).isEqualTo("approved");
    }

    @Test
    void rejectShowsTheReason() throws Exception {
        String me = account();
        String t = trx();
        client.send("POST", "/api/payments", me, pay("premium", t));
        assertThat(client.send("POST", "/api/admin/payments/" + pendingId(t) + "/reject", boss, "{\"reason\":\"TrxID not found\"}").status()).isEqualTo(204);
        var mine = client.send("GET", "/api/payments/me", me, "").body();
        assertThat((String) JsonPath.read(mine, "$.status")).isEqualTo("rejected");
        assertThat((String) JsonPath.read(mine, "$.reason")).isEqualTo("TrxID not found");
        assertThat(plan(me)).isEqualTo("free");
    }

    @Test
    void oneTrxIdOnce() throws Exception {
        String t = trx();
        String a = account();
        client.send("POST", "/api/payments", a, pay("basic", t));
        client.send("POST", "/api/admin/payments/" + pendingId(t) + "/reject", boss, "{\"reason\":\"Other\"}");
        var again = client.send("POST", "/api/payments", account(), pay("basic", t));
        assertThat(again.status()).isEqualTo(422);
        assertThat(again.body()).contains("trx-used");
    }

    /** Review (P2e): delete everything must not free a used TrxID (one payment, two months). */
    @Test
    void aTrxIdStaysUsedAfterDeleteEverything() throws Exception {
        String me = account();
        String t = trx();
        client.send("POST", "/api/payments", me, pay("basic", t));
        client.send("POST", "/api/admin/payments/" + pendingId(t) + "/approve", boss, "{}");
        assertThat(client.send("POST", "/api/me/delete", me, "{\"confirm\":\"DELETE\"}").status()).isEqualTo(204);
        var again = client.send("POST", "/api/payments", account(), pay("basic", t));
        assertThat(again.status()).isEqualTo(422);
        assertThat(again.body()).contains("trx-used");
    }

    /** Review (P2e): a paid plan never runs underneath another one; it starts when the current plan ends. */
    @Test
    void payingForAnotherPlanStartsWhenTheCurrentOneEnds() throws Exception {
        String me = account();
        String t1 = trx();
        client.send("POST", "/api/payments", me, pay("premium", t1));
        client.send("POST", "/api/admin/payments/" + pendingId(t1) + "/approve", boss, "{}");
        String t2 = trx();
        client.send("POST", "/api/payments", me, pay("basic", t2));
        client.send("POST", "/api/admin/payments/" + pendingId(t2) + "/approve", boss, "{}");
        List<java.sql.Timestamp[]> rows = jdbc.sql("select starts_at, ends_at from subscription where note in (?, ?) order by starts_at")
                .params(t1, t2).query((rs, n) -> new java.sql.Timestamp[] {rs.getTimestamp(1), rs.getTimestamp(2)}).list();
        assertThat(rows.get(1)[0]).isEqualTo(rows.get(0)[1]);
    }

    @Test
    void onePendingAtATime() throws Exception {
        String me = account();
        client.send("POST", "/api/payments", me, pay("basic", trx()));
        var second = client.send("POST", "/api/payments", me, pay("premium", trx()));
        assertThat(second.status()).isEqualTo(422);
        assertThat(second.body()).contains("payment-pending");
    }

    @Test
    void badTrxIdOrNumberIs422() throws Exception {
        String me = account();
        assertThat(client.send("POST", "/api/payments", me, pay("basic", "AB-12")).status()).isEqualTo(422);
        assertThat(client.send("POST", "/api/payments", me, pay("gold", trx())).status()).isEqualTo(422);
        assertThat(client.send("POST", "/api/payments", me,
                "{\"plan\":\"basic\",\"trxId\":\"" + trx() + "\",\"payerNumber\":\"0123\"}").status()).isEqualTo(422);
    }

    @Test
    void aGuestCantPay() throws Exception {
        var r = client.send("POST", "/api/payments", client.consentedGuest(5), pay("basic", trx()));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("sign-in");
        assertThat(client.send("POST", "/api/payments", null, pay("basic", trx())).status()).isEqualTo(401);
    }

    @Test
    void bkashOffWithoutANumber() throws Exception {
        bkash("");
        try {
            assertThat(client.send("GET", "/api/payments/bkash", null, "").status()).isEqualTo(204);
            var r = client.send("POST", "/api/payments", account(), pay("basic", trx()));
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.body()).contains("bkash-off");
        } finally {
            bkash("01712345678");
        }
    }

    @Test
    void aBadBkashNumberIsRefused() throws Exception {
        assertThat(client.send("PUT", "/api/admin/settings", boss, "{\"dailyTalkCap\":300,\"bkashNumber\":\"12345\"}").status()).isEqualTo(422);
    }

    @Test
    void twoApprovesGrantOnce() throws Exception {
        String me = account();
        String t = trx();
        client.send("POST", "/api/payments", me, pay("basic", t));
        long id = pendingId(t);
        var a = CompletableFuture.supplyAsync(() -> { try { return client.send("POST", "/api/admin/payments/" + id + "/approve", boss, "{}").status(); } catch (Exception e) { throw new RuntimeException(e); } });
        var b = CompletableFuture.supplyAsync(() -> { try { return client.send("POST", "/api/admin/payments/" + id + "/approve", boss, "{}").status(); } catch (Exception e) { throw new RuntimeException(e); } });
        a.join();
        b.join();
        assertThat(jdbc.sql("select count(*) from subscription where source = 'bkash' and note = ?").param(t).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void approvingTheSamePlanChains() throws Exception {
        String me = account();
        String t1 = trx();
        client.send("POST", "/api/payments", me, pay("basic", t1));
        client.send("POST", "/api/admin/payments/" + pendingId(t1) + "/approve", boss, "{}");
        String t2 = trx();
        client.send("POST", "/api/payments", me, pay("basic", t2));
        client.send("POST", "/api/admin/payments/" + pendingId(t2) + "/approve", boss, "{}");
        List<java.sql.Timestamp[]> rows = jdbc.sql("select starts_at, ends_at from subscription where note in (?, ?) order by starts_at")
                .params(t1, t2).query((rs, n) -> new java.sql.Timestamp[] {rs.getTimestamp(1), rs.getTimestamp(2)}).list();
        assertThat(rows.get(1)[0]).isEqualTo(rows.get(0)[1]);
    }

    @Test
    void onlyTheAdminDecides() throws Exception {
        String me = account();
        String t = trx();
        client.send("POST", "/api/payments", me, pay("basic", t));
        long id = pendingId(t);
        assertThat(client.send("GET", "/api/admin/payments", me, "").status()).isEqualTo(404);
        assertThat(client.send("POST", "/api/admin/payments/" + id + "/approve", me, "{}").status()).isEqualTo(404);
        assertThat(plan(me)).isEqualTo("free");
    }

    @Test
    void sixInADayIs429() throws Exception {
        String me = account();
        for (int i = 0; i < 5; i++) {
            String t = trx();
            client.send("POST", "/api/payments", me, pay("basic", t));
            client.send("POST", "/api/admin/payments/" + pendingId(t) + "/reject", boss, "{\"reason\":\"Other\"}");
        }
        assertThat(client.send("POST", "/api/payments", me, pay("basic", trx())).status()).isEqualTo(429);
    }
}
