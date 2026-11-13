package app.bolo.talk.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkContext;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.memory.application.port.in.MemoryUseCase;
import app.bolo.memory.application.port.in.TalkBrief;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.speech.application.port.in.StreamTranscriptionUseCase;
import app.bolo.speech.application.port.in.SynthesizeUseCase;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkConnection;
import app.bolo.talk.application.port.in.TalkOutput;
import app.bolo.talk.application.port.in.TalkRelayUseCase;
import app.bolo.talk.application.port.in.TalkStarted;
import app.bolo.talk.application.port.in.TalkView;
import app.bolo.talk.application.port.out.TurnPort;
import app.bolo.talk.domain.BuddyPrompt;
import app.bolo.talk.domain.TurnPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class TalkRelayService implements TalkRelayUseCase {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TalkRelayService.class);

    private final StreamTranscriptionUseCase recogniser;
    private final SynthesizeUseCase voice;
    private final LlmClient llm;
    private final StartTalkUseCase talks;
    private final TurnPort turns;
    private final AudioStoreUseCase audioStore;
    private final ApplicationEventPublisher events;
    private final SafetyGuard safety;
    private final Starters starters;
    private final TalkSettings settings;
    private final MemoryUseCase memory;

    public TalkRelayService(StreamTranscriptionUseCase recogniser, SynthesizeUseCase voice, LlmClient llm,
                            StartTalkUseCase talks, TurnPort turns, AudioStoreUseCase audioStore,
                            ApplicationEventPublisher events, SafetyGuard safety, Starters starters, MemoryUseCase memory,
                            @Value("${bolo.talk.speculate-after-ms}") long speculateAfterMs,
                            @Value("${bolo.talk.end-after-ms}") long endAfterMs,
                            @Value("${bolo.talk.unfinished-end-after-ms}") long unfinishedEndAfterMs,
                            @Value("${bolo.talk.unfinished-words}") String unfinishedWords,
                            @Value("${bolo.talk.minute-ms}") long minuteMs,
                            @Value("${bolo.talk.wrap-grace-ms}") long wrapGraceMs,
                            @Value("${bolo.talk.reply-timeout-ms}") long replyTimeoutMs,
                            @Value("${bolo.talk.no-audio-timeout-ms}") long noAudioTimeoutMs,
                            @Value("${bolo.talk.burst-allowance-ms}") long burstAllowanceMs,
                            @Value("${bolo.talk.quiet-encourage-ms}") long quietEncourageMs,
                            @Value("${bolo.talk.quiet-hint-ms}") long quietHintMs,
                            @Value("${bolo.talk.quiet-pause-ms}") long quietPauseMs,
                            @Value("${bolo.talk.paused-max-ms}") long pausedMaxMs,
                            @Value("${bolo.llm.buddy-model}") String model,
                            @Value("${bolo.llm.max-tokens}") int maxTokens) {
        this.recogniser = recogniser;
        this.voice = voice;
        this.llm = llm;
        this.talks = talks;
        this.turns = turns;
        this.audioStore = audioStore;
        this.events = events;
        this.safety = safety;
        this.starters = starters;
        this.memory = memory;
        var words = Arrays.stream(unfinishedWords.split(",")).map(w -> w.trim().toLowerCase(Locale.ROOT))
                .filter(w -> !w.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.settings = new TalkSettings(new TurnPolicy(speculateAfterMs, endAfterMs, unfinishedEndAfterMs, words),
                model, maxTokens, minuteMs, wrapGraceMs, replyTimeoutMs,
                noAudioTimeoutMs, burstAllowanceMs, quietEncourageMs, quietHintMs, quietPauseMs, pausedMaxMs);
    }

    @Override
    public TalkConnection open(TalkView talk, LearnerId learner, TalkOutput out) {
        TalkBrief brief = brief(learner, talk);
        TalkConversation c = new TalkConversation(talk, new TalkContext(learner, talk.id()), out, recogniser, voice, llm,
                talks, turns, audioStore, settings, () -> System.nanoTime() / 1_000_000, events::publishEvent, safety, starters,
                brief.text(), brief.followUp() ? BuddyPrompt.Opening.FOLLOW_UP : brief.known() ? BuddyPrompt.Opening.FROM_NOTES
                        : BuddyPrompt.Opening.FROM_GOAL);
        c.start();
        events.publishEvent(new TalkStarted(talk.id(), learner));
        return c;
    }

    /** P3b: what Bolo remembers. A failure here never stops a talk: it opens without the brief. */
    private TalkBrief brief(LearnerId learner, TalkView talk) {
        try {
            return memory.brief(learner, talk.id(), talk.goal());
        } catch (RuntimeException e) {
            log.warn("memory brief failed for talk {}: {}", talk.id(), e.getClass().getSimpleName());
            return TalkBrief.NONE;
        }
    }
}
