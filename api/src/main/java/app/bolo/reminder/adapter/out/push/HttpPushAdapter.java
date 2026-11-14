package app.bolo.reminder.adapter.out.push;

import app.bolo.reminder.application.port.out.PushPort;
import app.bolo.reminder.domain.Vapid;
import app.bolo.reminder.domain.WebPushCrypto;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Web Push over HTTP: RFC 8291 encryption, RFC 8292 VAPID, a day to live. */
@Slf4j
class HttpPushAdapter implements PushPort {

    private final Vapid vapid;
    private final Duration timeout;
    private final HttpClient http;

    HttpPushAdapter(Vapid vapid, Duration timeout) {
        this.vapid = vapid;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Outcome send(Subscription s, byte[] payload) {
        try {
            var dec = Base64.getUrlDecoder();
            byte[] body = WebPushCrypto.encrypt(payload, Vapid.publicKey(dec.decode(s.p256dh())), dec.decode(s.auth()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(s.endpoint())).timeout(timeout)
                    .header("TTL", "86400").header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                    .header("Authorization", vapid.authorization(s.endpoint(), Instant.now()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) return Outcome.GONE;
            if (status >= 200 && status < 300) return Outcome.SENT;
            log.warn("push to {} answered {}", host(s.endpoint()), status);
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (Exception e) {
            log.warn("push to {} failed: {}", host(s.endpoint()), e.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    @Override
    public String publicKey() {
        return vapid.publicKey();
    }

    /** Endpoints carry a per-browser token; only the host goes in logs. */
    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
