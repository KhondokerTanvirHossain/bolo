package app.bolo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/** Reads a test WAV's PCM samples by finding its "data" chunk (macOS afconvert adds a padding chunk before it). */
public final class WavFixture {

    private WavFixture() {
    }

    public static byte[] pcm(String resource) {
        byte[] wav;
        try (InputStream in = WavFixture.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("missing " + resource);
            wav = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int i = 12;
        while (i + 8 <= wav.length) {
            String id = new String(wav, i, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = (wav[i + 4] & 0xff) | (wav[i + 5] & 0xff) << 8 | (wav[i + 6] & 0xff) << 16 | (wav[i + 7] & 0xff) << 24;
            if (id.equals("data")) return Arrays.copyOfRange(wav, i + 8, Math.min(wav.length, i + 8 + size));
            i += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("no data chunk in " + resource);
    }
}
