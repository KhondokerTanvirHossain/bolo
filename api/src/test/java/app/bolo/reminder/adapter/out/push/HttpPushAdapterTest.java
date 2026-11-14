package app.bolo.reminder.adapter.out.push;

import app.bolo.reminder.application.port.out.PushPort;
import app.bolo.reminder.domain.Vapid;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPushAdapterTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(201);
    private final Map<String, String> headers = new ConcurrentHashMap<>();
    private volatile long delayMs;

    private PushPort.Subscription start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/push", ex -> {
            ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.getFirst()));
            headers.put("method", ex.getRequestMethod());
            ex.getRequestBody().readAllBytes();
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            ex.sendResponseHeaders(status.get(), -1);
            ex.close();
        });
        server.start();
        var ua = Vapid.generate();
        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(Vapid.encodePoint((ECPublicKey) ua.getPublic()));
        return new PushPort.Subscription("http://localhost:" + server.getAddress().getPort() + "/push/abc", p256dh,
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
    }

    private HttpPushAdapter adapter(Duration timeout) {
        var keys = Vapid.generate();
        return new HttpPushAdapter(new Vapid(Vapid.encodePublic(keys), Vapid.encodePrivate(keys), "mailto:x@y.z"), timeout);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsEncryptedWithVapid() throws Exception {
        var sub = start();
        assertThat(adapter(Duration.ofSeconds(5)).send(sub, "{\"body\":\"hi\"}".getBytes())).isEqualTo(PushPort.Outcome.SENT);
        assertThat(headers.get("method")).isEqualTo("POST");
        assertThat(headers.get("ttl")).isEqualTo("7200");
        assertThat(headers.get("content-encoding")).isEqualTo("aes128gcm");
        assertThat(headers.get("urgency")).isEqualTo("high");
        assertThat(headers.get("authorization")).startsWith("vapid t=").contains(", k=");
    }

    @Test
    void goneEndpoints() throws Exception {
        var sub = start();
        status.set(410);
        assertThat(adapter(Duration.ofSeconds(5)).send(sub, "x".getBytes())).isEqualTo(PushPort.Outcome.GONE);
        status.set(404);
        assertThat(adapter(Duration.ofSeconds(5)).send(sub, "x".getBytes())).isEqualTo(PushPort.Outcome.GONE);
        // Review (reminders, I5): a subscription made with another VAPID key answers 401/403 forever.
        status.set(403);
        assertThat(adapter(Duration.ofSeconds(5)).send(sub, "x".getBytes())).isEqualTo(PushPort.Outcome.GONE);
        status.set(401);
        assertThat(adapter(Duration.ofSeconds(5)).send(sub, "x".getBytes())).isEqualTo(PushPort.Outcome.GONE);
    }

    @Test
    void failuresNeverThrow() throws Exception {
        var sub = start();
        status.set(500);
        assertThat(adapter(Duration.ofSeconds(5)).send(sub, "x".getBytes())).isEqualTo(PushPort.Outcome.FAILED);
        status.set(201);
        delayMs = 1500;
        assertThat(adapter(Duration.ofMillis(300)).send(sub, "x".getBytes())).isEqualTo(PushPort.Outcome.FAILED);
        assertThat(adapter(Duration.ofSeconds(1)).send(new PushPort.Subscription("http://localhost:1/x", sub.p256dh(), sub.auth()),
                "x".getBytes())).isEqualTo(PushPort.Outcome.FAILED);
    }
}
