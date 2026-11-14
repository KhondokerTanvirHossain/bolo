package app.bolo.reminder.domain;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * VAPID (RFC 8292): the server's P-256 key pair and the {@code Authorization: vapid t=<jwt>, k=<key>} header every push
 * carries. Keys: public as the base64url 65-byte uncompressed point (what browsers take as applicationServerKey),
 * private as base64 PKCS#8 (what {@code openssl pkcs8 -topk8} writes).
 */
public final class Vapid {

    private static final ECParameterSpec P256 = params();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keys;
    private final String publicKey;
    private final String subject;

    public Vapid(String publicKey, String privatePkcs8, String subject) {
        this.keys = new KeyPair(publicKey(Base64.getUrlDecoder().decode(publicKey)), privateKey(privatePkcs8));
        this.publicKey = URL.encodeToString(encodePoint((ECPublicKey) keys.getPublic()));
        this.subject = subject;
    }

    public String publicKey() {
        return publicKey;
    }

    /** The header for one push to {@code endpoint}: audience is the endpoint's origin, valid 12 hours. */
    public String authorization(String endpoint, Instant now) {
        URI uri = URI.create(endpoint);
        String aud = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String head = URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = URL.encodeToString(("{\"aud\":\"" + aud + "\",\"exp\":" + (now.getEpochSecond() + 12 * 3600)
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        try {
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initSign(keys.getPrivate());
            sig.update((head + "." + claims).getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + head + "." + claims + "." + URL.encodeToString(sig.sign()) + ", k=" + publicKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- P-256 keys ------------------------------------------------------------------------------------------------

    public static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static KeyPair keyPair(String publicKey, ECPrivateKey privateKey) {
        return new KeyPair(publicKey(Base64.getUrlDecoder().decode(publicKey)), privateKey);
    }

    public static ECPublicKey publicKey(byte[] point) {
        if (point.length != 65 || point[0] != 4) throw new IllegalArgumentException("not an uncompressed P-256 point");
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)), new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("bad P-256 public key", e);
        }
    }

    public static ECPrivateKey rawPrivate(byte[] d) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("bad P-256 private key", e);
        }
    }

    private static ECPrivateKey privateKey(String pkcs8) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(pkcs8)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("bad VAPID private key", e);
        }
    }

    public static byte[] encodePoint(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static String encodePublic(KeyPair keys) {
        return URL.encodeToString(encodePoint((ECPublicKey) keys.getPublic()));
    }

    public static String encodePrivate(KeyPair keys) {
        return Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
    }

    private static void copy32(BigInteger v, byte[] out, int at) {
        byte[] b = v.toByteArray();
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, at + 32 - len, len);
    }

    private static ECParameterSpec params() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
