package app.bolo.talk.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkContext;
import app.bolo.common.id.TalkSessionId;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.llm.application.service.MeteredLlmClient;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import app.bolo.speech.adapter.out.fake.FakeTextToSpeech;
import app.bolo.speech.application.service.SpeechService;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkOutput;
import app.bolo.talk.application.port.in.TalkView;
import app.bolo.talk.application.port.out.TurnPort;
import app.bolo.talk.domain.TurnPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TalkConversationTest {

    /** Review #6: when the browser goes away, the output is closed too, so its writer thread ends. */
    @Test
    void aClosedSocketClosesTheOutput() throws Exception {
        AtomicBoolean outClosed = new AtomicBoolean();
        TalkOutput out = new TalkOutput() {
            @Override
            public void send(Map<String, Object> message) {
            }

            @Override
            public void audio(byte[] pcm24k) {
            }

            @Override
            public void close(boolean error) {
                outClosed.set(true);
            }
        };
        var usage = (app.bolo.common.usage.UsageRecorder) (l, s, v, k, u, t) -> { };
        var speech = new SpeechService(new FakeSpeechToText(), new FakeTextToSpeech(), usage, new app.bolo.speech.adapter.out.fake.FakeScriptedAssessment());
        var settings = new TalkSettings(new TurnPolicy(100, 300, 500, Set.of()), "fake", 100, 60_000, 60_000, 20_000,
                15_000, 30_000, 8_000, 15_000, 45_000, 1_800_000);
        TalkView talk = new TalkView(TalkSessionId.newId(), 5, null, null);
        var c = new TalkConversation(talk, new TalkContext(LearnerId.newId(), talk.id()), out, speech, speech,
                new MeteredLlmClient(new FakeLlm(), usage), mock(StartTalkUseCase.class), mock(TurnPort.class),
                mock(app.bolo.media.application.port.in.AudioStoreUseCase.class), settings,
                () -> System.nanoTime() / 1_000_000, e -> { },
                mock(SafetyGuard.class), mock(Starters.class));
        c.start();
        Thread.sleep(100);
        c.onClosed();
        assertThat(outClosed).isTrue();
    }

    /** Live finding: the recogniser's final text adds capitals and punctuation to the partial the speculation used. */
    @Test
    void theSameWordsWithDifferentPunctuationAreTheSameUtterance() {
        assertThat(TalkConversation.sameWords("yesterday i fix her but in the payment service",
                "Yesterday I fix her but, in the payment service.")).isTrue();
        assertThat(TalkConversation.sameWords("i like cricket", "I like cricket and football.")).isFalse();
        assertThat(TalkConversation.sameWords("", "")).isTrue();
    }
}
