package app.bolo.media;

import app.bolo.media.adapter.out.file.EncryptedFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NFR-13: voice recordings are encrypted at rest (AES-256-GCM). */
class EncryptedFileStoreTest {

    @TempDir Path dir;

    static byte[] key() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }

    @Test
    void roundTripsAndNeverStoresPlaintext() throws Exception {
        EncryptedFileStore store = new EncryptedFileStore(dir, key());
        byte[] audio = "RIFF....WAVEfmt the learner's voice".getBytes(StandardCharsets.UTF_8);
        store.put("talk/abc/turn-1.wav", audio);
        assertThat(store.get("talk/abc/turn-1.wav")).contains(audio);
        byte[] onDisk = Files.readAllBytes(dir.resolve("talk/abc/turn-1.wav"));
        assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("learner's voice");
    }

    @Test
    void aTamperedOrForeignFileReadsAsMissing() throws Exception {
        byte[] k = key();
        EncryptedFileStore store = new EncryptedFileStore(dir, k);
        store.put("talk/abc/turn-1.wav", new byte[]{1, 2, 3, 4});
        Path file = dir.resolve("talk/abc/turn-1.wav");
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 1;
        Files.write(file, bytes);
        assertThat(store.get("talk/abc/turn-1.wav")).isEmpty();

        store.put("talk/abc/turn-2.wav", new byte[]{5, 6});
        assertThat(new EncryptedFileStore(dir, key()).get("talk/abc/turn-2.wav")).isEmpty();
        assertThat(store.get("talk/abc/nothing.wav")).isEmpty();
    }

    @Test
    void deletesAPrefixAndRefusesKeysOutsideItsDirectory() {
        EncryptedFileStore store = new EncryptedFileStore(dir, key());
        store.put("talk/abc/turn-1.wav", new byte[]{1});
        store.put("talk/abc/turn-3.wav", new byte[]{1});
        store.put("talk/xyz/turn-1.wav", new byte[]{1});
        store.deletePrefix("talk/abc/");
        assertThat(store.get("talk/abc/turn-1.wav")).isEmpty();
        assertThat(store.get("talk/xyz/turn-1.wav")).isPresent();
        assertThatThrownBy(() -> store.put("../escape.wav", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
