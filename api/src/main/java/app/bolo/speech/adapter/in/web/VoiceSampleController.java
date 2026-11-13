package app.bolo.speech.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.speech.application.port.in.VoiceSampleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Settings → Bolo's voice → "Hear it" (P3c, SET-5): any learner with a session. */
@RestController
@RequiredArgsConstructor
class VoiceSampleController {

    private final VoiceSampleUseCase samples;

    @GetMapping("/api/voices/{key}/sample")
    ResponseEntity<byte[]> sample(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                  @PathVariable String key) {
        if (learner == null) throw new SignedOutException();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(samples.sample(learner, key));
    }
}
