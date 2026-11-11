package app.bolo.billing.adapter.in.web;

import app.bolo.billing.application.port.in.ReferralUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** P2d: Me → Invite friends, and the Home note when a month is earned. */
@RestController
@RequiredArgsConstructor
class ReferralController {

    private final ReferralUseCase referrals;

    @GetMapping("/api/referral")
    ReferralUseCase.Status status(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return referrals.status(learner);
    }

    @PostMapping("/api/referral/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void seen(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        referrals.seen(learner);
    }
}
