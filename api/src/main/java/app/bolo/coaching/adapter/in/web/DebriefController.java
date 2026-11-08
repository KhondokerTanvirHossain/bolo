package app.bolo.coaching.adapter.in.web;

import app.bolo.coaching.application.port.in.DebriefUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.web.CurrentLearner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/** The Coach's debrief and its audio, only ever for the talk's own learner (404 otherwise, never "forbidden"). */
@RestController
@RequiredArgsConstructor
class DebriefController {

    private final DebriefUseCase debriefs;

    @GetMapping("/api/talk/{id}/debrief")
    ResponseEntity<String> view(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                @PathVariable String id) {
        return talk(id).flatMap(t -> debriefs.view(signedIn(learner), t))
                .map(json -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(json))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/talk/{id}/debrief/fixes/{fixId}/dispute")
    ResponseEntity<Void> dispute(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                 @PathVariable String id, @PathVariable String fixId) {
        boolean done = talk(id).map(t -> debriefs.dispute(signedIn(learner), t, fixId)).orElse(false);
        return done ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/talk/{id}/debrief/viewed")
    ResponseEntity<Void> viewed(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                @PathVariable String id) {
        boolean done = talk(id).map(t -> debriefs.markViewed(signedIn(learner), t)).orElse(false);
        return done ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/api/media/talk/{id}/{file}")
    ResponseEntity<byte[]> media(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                 @PathVariable String id, @PathVariable String file) {
        return talk(id).flatMap(t -> debriefs.media(signedIn(learner), t, file))
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static LearnerId signedIn(LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return learner;
    }

    private static Optional<TalkSessionId> talk(String id) {
        try {
            return Optional.of(TalkSessionId.of(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
