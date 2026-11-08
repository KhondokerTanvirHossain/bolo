package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.Day1UseCase;
import app.bolo.learner.application.port.out.ConsentLedgerPort;
import app.bolo.learner.domain.Consent;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Stores the clip as the browser recorded it (Opus in WebM, or MP4 on Safari): no conversion, encrypted at rest. */
@Service
@RequiredArgsConstructor
public class Day1Service implements Day1UseCase {

    /** 30 s of Opus is ~150 KB; 2 MB leaves room for MP4 and longer intros. */
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final ConsentLedgerPort consents;
    private final AudioStoreUseCase audio;

    static String key(LearnerId learner, String ext) {
        return "learner/" + learner.value() + "/day1." + ext;
    }

    @Override
    public void save(LearnerId learner, byte[] clip) {
        if (!consents.latest(learner).map(Consent::keepRecordings).orElse(false)) throw new RefusedException("no-consent");
        String ext = kind(clip).orElseThrow(() -> new RefusedException("bad-audio"));
        audio.delete(key(learner, "webm"));
        audio.delete(key(learner, "mp4"));
        audio.put(key(learner, ext), clip);
    }

    @Override
    public Optional<Clip> find(LearnerId learner) {
        return audio.get(key(learner, "webm")).map(b -> new Clip("audio/webm", b))
                .or(() -> audio.get(key(learner, "mp4")).map(b -> new Clip("audio/mp4", b)));
    }

    /** By the file's own signature, never the declared type: WebM (EBML header) or MP4 ("ftyp" box). */
    static Optional<String> kind(byte[] b) {
        if (b == null || b.length < 12 || b.length > MAX_BYTES) return Optional.empty();
        if ((b[0] & 0xff) == 0x1A && (b[1] & 0xff) == 0x45 && (b[2] & 0xff) == 0xDF && (b[3] & 0xff) == 0xA3) return Optional.of("webm");
        if ("ftyp".equals(new String(b, 4, 4, StandardCharsets.US_ASCII))) return Optional.of("mp4");
        return Optional.empty();
    }
}
