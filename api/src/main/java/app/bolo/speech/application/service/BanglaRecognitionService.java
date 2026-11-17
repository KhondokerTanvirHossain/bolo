package app.bolo.speech.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.speech.application.port.in.BanglaRecognitionUseCase;
import app.bolo.speech.application.port.in.HeardBangla;
import app.bolo.speech.application.port.out.BanglaSpeechPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Founder 2026-11-18: a turn the English recogniser was unsure of, heard once in Bangla. Logs never carry the text. */
@Slf4j
@Service
public class BanglaRecognitionService implements BanglaRecognitionUseCase {

    /** The REST short-audio limit is 60 s of 16 kHz 16-bit mono, plus the WAV header. */
    static final int MAX_WAV_BYTES = 60 * 32_000 + 44;

    private final BanglaSpeechPort port;

    /** No vendor configured: never heard (the talk goes on as before). */
    public BanglaRecognitionService(org.springframework.beans.factory.ObjectProvider<BanglaSpeechPort> port) {
        this.port = port.getIfAvailable();
    }

    @Override
    public Optional<HeardBangla> recognise(TalkContext ctx, byte[] wav16k, long timeoutMs) {
        if (port == null || wav16k == null || wav16k.length <= 44 || wav16k.length > MAX_WAV_BYTES) return Optional.empty();
        try {
            return port.recognise(wav16k, timeoutMs);
        } catch (RuntimeException e) {
            log.warn("bangla recognition failed for talk {}: {}", ctx.sessionId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
