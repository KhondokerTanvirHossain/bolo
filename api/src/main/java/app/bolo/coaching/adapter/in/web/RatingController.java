package app.bolo.coaching.adapter.in.web;

import app.bolo.coaching.application.port.in.RatingUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.web.CurrentLearner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/** DBF-8: "How was today?" for the talk's own learner (404 otherwise). */
@RestController
@RequiredArgsConstructor
class RatingController {

    private final RatingUseCase ratings;

    record RatingRequest(Integer score, String note) {
    }

    @GetMapping("/api/talk/{id}/rating")
    ResponseEntity<Map<String, Boolean>> ask(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                             @PathVariable String id) {
        LearnerId me = signedIn(learner);
        return talk(id).flatMap(t -> ratings.ask(me, t))
                .map(ask -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("ask", ask)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/talk/{id}/rating")
    ResponseEntity<Void> save(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                              @PathVariable String id, @RequestBody RatingRequest body) {
        LearnerId me = signedIn(learner);
        boolean saved = talk(id).map(t -> ratings.save(me, t, body.score(), body.note())).orElse(false);
        return saved ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
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
