package app.bolo.speech;

import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import app.bolo.speech.adapter.out.fake.FakeTextToSpeech;
import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class FakeSpeechTest {

    /** 20 ms of 16 kHz PCM: a -20 dBFS tone, or zeros. */
    static byte[] frame(boolean loud) {
        ByteBuffer b = ByteBuffer.allocate(640).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 320; i++) b.putShort(loud ? (short) (4634 * Math.sin(2 * Math.PI * 440 * i / 16000.0)) : 0);
        return b.array();
    }

    @Test
    void fakeRecogniserHearsHelloBoloAfter300msOfSpeech() {
        List<String> partials = new CopyOnWriteArrayList<>();
        FakeSpeechToText stt = new FakeSpeechToText();
        TranscriptionStream stream = stt.open(listener(partials));
        for (int i = 0; i < 14; i++) stream.write(frame(true));
        assertThat(partials).isEmpty();
        stream.write(frame(true));
        assertThat(partials).containsExactly("hello bolo");
        assertThat(stt.streams().getFirst().loudFrames()).isEqualTo(15);
    }

    @Test
    void markStartsAFreshUtterance() {
        List<String> partials = new CopyOnWriteArrayList<>();
        TranscriptionStream stream = new FakeSpeechToText().open(listener(partials));
        for (int i = 0; i < 15; i++) stream.write(frame(true));
        stream.mark();
        for (int i = 0; i < 14; i++) stream.write(frame(true));
        assertThat(partials).hasSize(1);
        stream.write(frame(true));
        assertThat(partials).hasSize(2);
    }

    @Test
    void zerosAreNotSpeech() {
        FakeSpeechToText stt = new FakeSpeechToText();
        TranscriptionStream stream = stt.open(listener(new CopyOnWriteArrayList<>()));
        for (int i = 0; i < 50; i++) stream.write(frame(false));
        assertThat(stt.streams().getFirst().loudFrames()).isZero();
    }

    @Test
    void fakeVoiceGives1200BytesPerCharacter() {
        List<byte[]> chunks = new CopyOnWriteArrayList<>();
        new FakeTextToSpeech().synthesize("Hi, I'm Bolo.", chunks::add);
        assertThat(chunks.stream().mapToInt(c -> c.length).sum()).isEqualTo("Hi, I'm Bolo.".length() * 1200);
    }

    static TranscriptListener listener(List<String> partials) {
        return new TranscriptListener() {
            @Override
            public void onPartial(String textSoFar) {
                partials.add(textSoFar);
            }

            @Override
            public void onFailure(Exception e) {
                throw new AssertionError(e);
            }
        };
    }
}
