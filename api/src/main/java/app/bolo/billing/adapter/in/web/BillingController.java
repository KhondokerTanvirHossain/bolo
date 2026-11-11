package app.bolo.billing.adapter.in.web;

import app.bolo.billing.application.port.in.WaitlistUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.talk.application.port.in.PlansUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** P2c: the public plans (the pricing page works signed out) and the waitlist. */
@RestController
@RequiredArgsConstructor
class BillingController {

    private final PlansUseCase plans;
    private final WaitlistUseCase waitlist;

    record Join(String plan, String region, String timeZone) {
    }

    @GetMapping("/api/plans")
    Map<String, Object> plans() {
        return Map.of("plans", plans.plans());
    }

    @PostMapping("/api/waitlist")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void join(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @RequestBody Join body) {
        if (learner == null) throw new SignedOutException();
        waitlist.join(learner, body.plan(), body.region(), body.timeZone());
    }

    @GetMapping("/api/waitlist/me")
    ResponseEntity<WaitlistUseCase.Mine> mine(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return waitlist.mine(learner).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
