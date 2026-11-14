package app.bolo.reminder.adapter.out.push;

import app.bolo.reminder.domain.Vapid;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Review (reminders, I5 / minor 1): missing or broken keys switch reminders off; they never stop the API starting. */
class PushConfigurationTest {

    @Test
    void webpushWithoutKeysIsUnavailable() {
        var port = new PushConfiguration().pushPort("webpush", "", "", "mailto:x@y.z");
        assertThat(port.available()).isFalse();
        assertThat(port.publicKey()).isNull();
    }

    @Test
    void brokenKeysAreUnavailableNotAStartupFailure() {
        var port = new PushConfiguration().pushPort("webpush", "-----BEGIN PUBLIC KEY-----", "not a key", "mailto:x@y.z");
        assertThat(port.available()).isFalse();
    }

    @Test
    void goodKeysAndTheFakeAreAvailable() {
        var keys = Vapid.generate();
        assertThat(new PushConfiguration().pushPort("webpush", Vapid.encodePublic(keys), Vapid.encodePrivate(keys), "mailto:x@y.z").available()).isTrue();
        assertThat(new PushConfiguration().pushPort("fake", "", "", "mailto:x@y.z").available()).isTrue();
    }
}
