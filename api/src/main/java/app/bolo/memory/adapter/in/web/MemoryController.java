package app.bolo.memory.adapter.in.web;

import app.bolo.common.error.RefusedException;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.memory.application.port.in.MemoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Me → What Bolo remembers (P3b, ACC-9 2026-11-13): any learner with a session, guest or account. */
@RestController
@RequiredArgsConstructor
class MemoryController {

    private final MemoryUseCase memory;

    record Switch(Boolean enabled) {
    }

    @GetMapping("/api/memory")
    MemoryUseCase.MemoryView view(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        return memory.view(signedIn(learner));
    }

    @DeleteMapping("/api/memory/lines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @PathVariable String id) {
        memory.remove(signedIn(learner), id);
    }

    @PutMapping("/api/memory")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setEnabled(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                    @RequestBody(required = false) Switch body) {
        LearnerId who = signedIn(learner);
        if (body == null || body.enabled() == null) throw new RefusedException("memory-enabled");
        memory.setEnabled(who, body.enabled());
    }

    @PostMapping("/api/memory/forget")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forget(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        memory.forget(signedIn(learner));
    }

    private static LearnerId signedIn(LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return learner;
    }
}
