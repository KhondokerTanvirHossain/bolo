package app.bolo.reminder.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class VapidTest {

    @Test
    void headerVerifies() throws Exception {
        var keys = Vapid.generate();
        Vapid vapid = new Vapid(Vapid.encodePublic(keys), Vapid.encodePrivate(keys), "mailto:k.tanvir.hossain@gmail.com");
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        String header = vapid.authorization("https://fcm.googleapis.com/fcm/send/abc:def", now);

        assertThat(header).startsWith("vapid t=").contains(", k=" + vapid.publicKey());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        var dec = Base64.getUrlDecoder();
        JsonNode head = JsonMapper.builder().build().readTree(dec.decode(parts[0]));
        JsonNode claims = JsonMapper.builder().build().readTree(dec.decode(parts[1]));
        assertThat(head.get("alg").asString()).isEqualTo("ES256");
        assertThat(claims.get("aud").asString()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("exp").asLong()).isEqualTo(now.getEpochSecond() + 12 * 3600);
        assertThat(claims.get("sub").asString()).isEqualTo("mailto:k.tanvir.hossain@gmail.com");

        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initVerify(keys.getPublic());
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(sig.verify(dec.decode(parts[2]))).isTrue();
    }

    @Test
    void publicKeyIsTheUncompressedPoint() {
        var keys = Vapid.generate();
        Vapid vapid = new Vapid(Vapid.encodePublic(keys), Vapid.encodePrivate(keys), "mailto:x@y.z");
        byte[] point = Base64.getUrlDecoder().decode(vapid.publicKey());
        assertThat(point).hasSize(65);
        assertThat(point[0]).isEqualTo((byte) 4);
    }
}
