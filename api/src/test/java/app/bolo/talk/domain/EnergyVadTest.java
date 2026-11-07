package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyVadTest {

    /** A 20 ms, 16 kHz frame of a 440 Hz tone at the given dBFS (RMS). */
    static short[] tone(double dbfs) {
        short[] f = new short[320];
        double amp = Math.pow(10, dbfs / 20) * Math.sqrt(2) * 32767;
        for (int i = 0; i < f.length; i++) f[i] = (short) Math.round(amp * Math.sin(2 * Math.PI * 440 * i / 16000.0));
        return f;
    }

    static short[] hiss(double dbfs, Random r) {
        short[] f = new short[320];
        double sd = Math.pow(10, dbfs / 20) * 32767;
        for (int i = 0; i < f.length; i++) f[i] = (short) Math.max(-32768, Math.min(32767, Math.round(r.nextGaussian() * sd)));
        return f;
    }

    @Test
    void aClearToneIsSpeech() {
        assertThat(new EnergyVad().isSpeech(tone(-20))).isTrue();
    }

    @Test
    void digitalSilenceIsNot() {
        assertThat(new EnergyVad().isSpeech(new short[320])).isFalse();
    }

    @Test
    void aSteadyHissBecomesTheFloorAndSpeechStillStandsOut() {
        EnergyVad vad = new EnergyVad();
        Random r = new Random(7);
        boolean last = true;
        for (int i = 0; i < 100; i++) last = vad.isSpeech(hiss(-45, r));
        assertThat(last).isFalse();
        assertThat(vad.isSpeech(tone(-20))).isTrue();
    }
}
