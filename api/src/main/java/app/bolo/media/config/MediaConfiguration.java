package app.bolo.media.config;

import app.bolo.media.adapter.out.file.EncryptedFileStore;
import app.bolo.media.application.port.out.ObjectStorePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Configuration
class MediaConfiguration {

    /** BOLO_MEDIA_KEY is 32 random bytes, base64. Without one (tests, keyless dev) a per-process key is used. */
    @Bean
    ObjectStorePort objectStore(@Value("${bolo.media.dir}") String dir, @Value("${bolo.media.key}") String key) {
        byte[] bytes;
        if (key == null || key.isBlank()) {
            log.warn("BOLO_MEDIA_KEY is not set: audio written now can't be read after a restart (fine for dev and tests only)");
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = Base64.getDecoder().decode(key.trim());
        }
        return new EncryptedFileStore(Path.of(dir), bytes);
    }
}
