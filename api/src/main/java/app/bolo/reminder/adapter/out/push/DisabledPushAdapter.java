package app.bolo.reminder.adapter.out.push;

import app.bolo.reminder.application.port.out.PushPort;

/** Web Push without usable VAPID keys: reminders are off (talks are unaffected). */
class DisabledPushAdapter implements PushPort {

    @Override
    public Outcome send(Subscription subscription, byte[] payload) {
        return Outcome.FAILED;
    }

    @Override
    public String publicKey() {
        return null;
    }
}
