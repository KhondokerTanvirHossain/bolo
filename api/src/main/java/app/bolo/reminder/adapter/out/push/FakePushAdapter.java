package app.bolo.reminder.adapter.out.push;

import app.bolo.reminder.application.port.out.PushPort;
import app.bolo.reminder.domain.Vapid;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tests and local runs: records each push instead of sending it; {@link #gone} makes an endpoint answer GONE. */
public class FakePushAdapter implements PushPort {

    public record Sent(Subscription subscription, String json) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> gone = ConcurrentHashMap.newKeySet();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private final String publicKey = Vapid.encodePublic(Vapid.generate());

    @Override
    public Outcome send(Subscription subscription, byte[] payload) {
        if (gone.contains(subscription.endpoint())) return Outcome.GONE;
        if (failing.contains(subscription.endpoint())) return Outcome.FAILED;
        sent.add(new Sent(subscription, new String(payload, StandardCharsets.UTF_8)));
        return Outcome.SENT;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public void gone(String endpoint) {
        gone.add(endpoint);
    }

    /** The push service for this endpoint is down: every send FAILED. */
    public void failing(String endpoint) {
        failing.add(endpoint);
    }

    public void clear() {
        sent.clear();
        gone.clear();
        failing.clear();
    }
}
