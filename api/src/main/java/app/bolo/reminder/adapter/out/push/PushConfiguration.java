package app.bolo.reminder.adapter.out.push;

import app.bolo.reminder.application.port.out.PushPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** {@code bolo.push.vendor}: webpush (real, needs the VAPID keys) or fake (tests, local; also webpush without keys). */
@lombok.extern.slf4j.Slf4j
@Configuration
class PushConfiguration {

    @Bean
    PushPort pushPort(@Value("${bolo.push.vendor}") String vendor,
                      @Value("${bolo.push.vapid.public-key:}") String publicKey,
                      @Value("${bolo.push.vapid.private-key:}") String privateKey,
                      @Value("${bolo.push.vapid.subject}") String subject) {
        if (!"webpush".equals(vendor)) return new FakePushAdapter();
        if (publicKey.isBlank() || privateKey.isBlank()) {
            // Reminders off rather than the whole API down: talks matter more than a missing key.
            log.error("bolo.push.vendor=webpush without BOLO_VAPID_PUBLIC_KEY / BOLO_VAPID_PRIVATE_KEY: reminders are not sent");
            return new FakePushAdapter();
        }
        return new HttpPushAdapter(new app.bolo.reminder.domain.Vapid(publicKey, privateKey, subject), Duration.ofSeconds(10));
    }
}
