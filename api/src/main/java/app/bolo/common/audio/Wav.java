package app.bolo.common.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Mono 16-bit PCM ⇄ WAV, and cutting a stretch of PCM by time. */
public final class Wav {

    private Wav() {
    }

    public static byte[] wrap(byte[] pcm, int sampleRate) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return b.array();
    }

    /** The PCM samples of a WAV (finds the "data" chunk); empty if it isn't one. */
    public static byte[] pcm(byte[] wav) {
        int i = 12;
        while (i + 8 <= wav.length) {
            String id = new String(wav, i, 4, StandardCharsets.US_ASCII);
            int size = ByteBuffer.wrap(wav, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (id.equals("data")) return Arrays.copyOfRange(wav, i + 8, Math.min(wav.length, i + 8 + Math.max(0, size)));
            i += 8 + Math.max(0, size) + (size & 1);
        }
        return new byte[0];
    }

    /** PCM between two times, clamped to what exists. */
    public static byte[] slice(byte[] pcm, int sampleRate, long startMs, long endMs) {
        int bytesPerMs = sampleRate * 2 / 1000;
        int from = (int) Math.max(0, Math.min(pcm.length, startMs * bytesPerMs)) & ~1;
        int to = (int) Math.max(from, Math.min(pcm.length, endMs * bytesPerMs)) & ~1;
        return Arrays.copyOfRange(pcm, from, to);
    }
}
