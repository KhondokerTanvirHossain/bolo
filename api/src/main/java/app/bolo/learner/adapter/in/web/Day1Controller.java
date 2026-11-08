package app.bolo.learner.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.Day1UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import app.bolo.common.error.RefusedException;
import java.io.IOException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The learner's own Day 1 clip (ONB-4, ACC-7). Only ever the signed-in learner's; 404 when there is none. */
@RestController
@RequiredArgsConstructor
class Day1Controller {

    private final Day1UseCase day1;

    /** Multipart (field "audio"), like practice attempts: the write guard accepts only JSON or multipart bodies. */
    @PutMapping(path = "/api/me/day1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void save(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
              @RequestParam("audio") MultipartFile audio) throws IOException {
        if (learner == null) throw new SignedOutException();
        if (audio.getSize() > 2L * 1024 * 1024) throw new RefusedException("bad-audio");
        day1.save(learner, audio.getBytes());
    }

    @GetMapping("/api/me/day1")
    ResponseEntity<byte[]> get(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return day1.find(learner)
                .map(c -> ResponseEntity.ok().contentType(MediaType.parseMediaType(c.contentType())).cacheControl(CacheControl.noStore()).body(c.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
