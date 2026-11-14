package app.bolo.reminder.application.port.out;

/** Sends one Web Push message to one browser. */
public interface PushPort {

    /** A browser's push subscription: endpoint, its P-256 key (p256dh) and auth secret, both base64url. */
    record Subscription(String endpoint, String p256dh, String auth) {
    }

    /** SENT: accepted; GONE: the subscription no longer exists (404/410), forget it; FAILED: try again another day. */
    enum Outcome { SENT, GONE, FAILED }

    /** Never throws. */
    Outcome send(Subscription subscription, byte[] payload);

    /** The VAPID public key browsers subscribe with (base64url). */
    String publicKey();
}
