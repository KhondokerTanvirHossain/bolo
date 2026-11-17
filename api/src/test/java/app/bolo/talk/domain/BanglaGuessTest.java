package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Founder 2026-11-18: Bolo understands Bangla. Spike: English recognition is 0.82-0.90 sure on English, under 0.25 on Bangla. */
class BanglaGuessTest {

    static final BanglaGuess G = new BanglaGuess(0.4, 0.6);

    @Test
    void onlyAnUnsureOrWordlessTurnIsChecked() {
        assertThat(G.worthChecking("Amar boss shesh mourte", 0.16)).isTrue();
        assertThat(G.worthChecking("", null)).as("speech with no words").isTrue();
        assertThat(G.worthChecking("I have to tell the client", 0.82)).isFalse();
        assertThat(G.worthChecking("I have to tell the client", null)).as("no score yet: as today").isFalse();
        assertThat(G.worthChecking("I think so", 0.41)).isFalse();
    }

    @Test
    void banglaScriptWithEnoughConfidenceIsBangla() {
        assertThat(G.accept("আমার বস শেষ মুহূর্তে পুরো পরিকল্পনা বদলে দিয়েছে।", 0.85, 0.16)).isTrue();
        assertThat(G.accept("আজকে আমার অফিস একটা ইম্পরট্যান্ট মিটিং ছিল", 0.78, 0.15)).as("mixed Bangla-English").isTrue();
        assertThat(G.accept("আমার বস", 0.55, 0.1)).as("not sure enough").isFalse();
        assertThat(G.accept("hello there", 0.9, 0.1)).as("not Bangla script").isFalse();
        assertThat(G.accept("", 0.9, 0.1)).isFalse();
        assertThat(G.accept("আমার বস", 0.62, 0.39)).as("only a little surer than the English").isFalse();
    }

    @Test
    void banglaScriptIsMostlyBanglaLetters() {
        assertThat(BanglaGuess.isBangla("আমি next week এ interview দিতে যাচ্ছি")).isTrue();
        assertThat(BanglaGuess.isBangla("I went to অফিস today and met my manager")).isFalse();
        assertThat(BanglaGuess.isBangla(null)).isFalse();
    }
}
