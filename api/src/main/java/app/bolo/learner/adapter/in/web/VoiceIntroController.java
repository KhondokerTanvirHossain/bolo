package app.bolo.learner.adapter.in.web;

import app.bolo.common.error.RefusedException;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.VoiceIntroUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Your voice, then and now (PRG-2, US-6.1). Only ever the signed-in learner's own; 404 when there is none. */
@RestController
@RequiredArgsConstructor
class VoiceIntroController {

    private final VoiceIntroUseCase voice;

    @GetMapping("/api/me/voice")
    VoiceIntroUseCase.Voice voice(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return voice.voice(learner);
    }

    /** Multipart: field "audio" when the clip is kept on the server; without it only the date is recorded. */
    @PostMapping(path = "/api/me/intro/{day}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void record(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner, @PathVariable int day,
                @RequestParam(name = "audio", required = false) MultipartFile audio) throws IOException {
        if (learner == null) throw new SignedOutException();
        if (audio != null && audio.getSize() > 2L * 1024 * 1024) throw new RefusedException("bad-audio");
        voice.record(learner, day, audio == null ? null : audio.getBytes());
    }

    @GetMapping("/api/me/intro/{day}")
    ResponseEntity<byte[]> clip(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
                                @PathVariable int day) {
        if (learner == null) throw new SignedOutException();
        return voice.clip(learner, day)
                .map(c -> ResponseEntity.ok().contentType(MediaType.parseMediaType(c.contentType())).cacheControl(CacheControl.noStore()).body(c.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
