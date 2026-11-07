package app.bolo.media.adapter.out.file;

import app.bolo.media.application.port.out.ObjectStorePort;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Files on a local volume, each AES-256-GCM encrypted with its own random 12-byte IV (file = IV ‖ ciphertext+tag).
 * A tampered file, or one written under another key, reads as missing (NFR-13). Keys can't leave the directory.
 */
@Slf4j
public class EncryptedFileStore implements ObjectStorePort {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path root;
    private final SecretKeySpec key;

    public EncryptedFileStore(Path root, byte[] key) {
        if (key.length != 32) throw new IllegalArgumentException("media key must be 32 bytes (AES-256)");
        this.root = root.toAbsolutePath().normalize();
        this.key = new SecretKeySpec(key, "AES");
    }

    @Override
    public void put(String name, byte[] bytes) {
        Path file = resolve(name);
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = c.doFinal(bytes);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("media encryption failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> get(String name) {
        Path file = resolve(name);
        try {
            byte[] all = Files.readAllBytes(file);
            if (all.length <= IV_BYTES) return Optional.empty();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOf(all, IV_BYTES)));
            return Optional.of(c.doFinal(all, IV_BYTES, all.length - IV_BYTES));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (GeneralSecurityException e) {
            log.warn("media file unreadable (tampered or another key): {}", name);
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        Path dir = resolve(prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix);
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("..") || name.contains("\\")) {
            throw new IllegalArgumentException("bad media key");
        }
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("bad media key");
        return p;
    }
}
