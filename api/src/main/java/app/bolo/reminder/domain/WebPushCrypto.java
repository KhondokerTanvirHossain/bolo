package app.bolo.reminder.domain;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/** Message encryption for Web Push (RFC 8291) in the aes128gcm content coding (RFC 8188), one record. */
public final class WebPushCrypto {

    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** Encrypts with a fresh key pair and salt, as every real push must. */
    public static byte[] encrypt(byte[] plaintext, ECPublicKey uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, Vapid.generate(), salt);
    }

    /** The body: salt ‖ rs ‖ idlen ‖ the server's public point ‖ AES-128-GCM(plaintext ‖ 0x02). */
    public static byte[] encrypt(byte[] plaintext, ECPublicKey uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(asKeys.getPrivate());
            ka.doPhase(uaPublic, true);
            byte[] shared = ka.generateSecret();
            byte[] uaPoint = Vapid.encodePoint(uaPublic);
            byte[] asPoint = Vapid.encodePoint((ECPublicKey) asKeys.getPublic());

            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPoint, asPoint);
            byte[] ikm = hkdf(authSecret, shared, keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] sealed = c.doFinal(concat(plaintext, new byte[]{2}));

            return ByteBuffer.allocate(16 + 4 + 1 + asPoint.length + sealed.length)
                    .put(salt).putInt(RECORD_SIZE).put((byte) asPoint.length).put(asPoint).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("web push encryption failed", e);
        }
    }

    /** HKDF-SHA256 (RFC 5869) for outputs up to one block. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        ByteBuffer b = ByteBuffer.allocate(n);
        for (byte[] p : parts) b.put(p);
        return b.array();
    }
}
