package app.bolo.reminder.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** RFC 8291 Appendix A: the worked example, byte for byte. */
class WebPushCryptoTest {

    private static byte[] b64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    @Test
    void rfc8291AppendixA() {
        KeyPair as = Vapid.keyPair("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
                Vapid.rawPrivate(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));
        byte[] body = WebPushCrypto.encrypt("When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                Vapid.publicKey(b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4")),
                b64("BTBZMqHH6r4Tts7J_aSIgg"), as, b64("DGv6ra1nlYgDCS1FRnbzlw"));
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void randomSaltAndKeysEachTime() {
        var ua = Vapid.generate();
        byte[] a = WebPushCrypto.encrypt("hi".getBytes(), (java.security.interfaces.ECPublicKey) ua.getPublic(), new byte[16]);
        byte[] b = WebPushCrypto.encrypt("hi".getBytes(), (java.security.interfaces.ECPublicKey) ua.getPublic(), new byte[16]);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.length).isEqualTo(16 + 4 + 1 + 65 + 2 + 1 + 16);
    }
}
