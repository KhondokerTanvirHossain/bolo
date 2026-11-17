package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Founder 2026-11-18: Bolo understands Bangla. Spike through the live SDK: the English recogniser is 0.91-0.95 sure on
 * English and 0.47-0.81 on Bangla or mixed speech; Azure's language identification decides the rest.
 */
class BanglaGuessTest {

    static final BanglaGuess G = new BanglaGuess(0.85, 0.6);

    @Test
    void onlyAnUnsureOrWordlessTurnIsChecked() {
        assertThat(G.worthChecking("I mean next weekend reviewed it at T.", 0.81)).as("mixed Bangla-English").isTrue();
        assertThat(G.worthChecking("Amar Bose she's Moody property coupon", 0.57)).isTrue();
        assertThat(G.worthChecking("", null)).as("speech with no words").isTrue();
        assertThat(G.worthChecking("Yesterday I went to office", 0.95)).isFalse();
        // Review 2026-11-18 (Important 1): a turn whose score hasn't arrived (a Done tap) is checked; the language check decides.
        assertThat(G.worthChecking("I have to tell the client", null)).as("no score yet").isTrue();
    }

    @Test
    void banglaScriptWithEnoughConfidenceIsBangla() {
        assertThat(G.accept("আমার বস শেষ মুহূর্তে পুরো পরিকল্পনা বদলে দিয়েছে।", 0.8, 0.57)).isTrue();
        assertThat(G.accept("আজকে আমার অফিস একটা ইম্পরট্যান্ট মিটিং ছিল", 0.8, 0.76)).as("mixed Bangla-English").isTrue();
        assertThat(G.accept("আমার বস", 0.55, 0.1)).as("not sure enough").isFalse();
        assertThat(G.accept("hello there", 0.9, 0.1)).as("not Bangla script").isFalse();
        assertThat(G.accept("", 0.9, 0.1)).isFalse();
    }

    @Test
    void banglaScriptIsMostlyBanglaLetters() {
        assertThat(BanglaGuess.isBangla("আমি next week এ interview দিতে যাচ্ছি")).isTrue();
        assertThat(BanglaGuess.isBangla("I went to অফিস today and met my manager")).isFalse();
        assertThat(BanglaGuess.isBangla(null)).isFalse();
    }
}
