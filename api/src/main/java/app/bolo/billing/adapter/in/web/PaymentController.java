package app.bolo.billing.adapter.in.web;

import app.bolo.billing.application.port.in.PaymentUseCase;
import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
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

/** P2e: pay with bKash (Send Money, then the TrxID). */
@RestController
@RequiredArgsConstructor
class PaymentController {

    private final PaymentUseCase payments;

    record Pay(String plan, String trxId, String payerNumber) {
    }

    /** Public: the pricing page shows the bKash option only when a number is set. */
    @GetMapping("/api/payments/bkash")
    ResponseEntity<Map<String, String>> bkash() {
        return payments.bkashNumber().map(n -> ResponseEntity.ok(Map.of("number", n))).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/payments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pay(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @RequestBody Pay body) {
        if (learner == null) throw new SignedOutException();
        payments.submit(learner, body.plan(), body.trxId(), body.payerNumber());
    }

    @GetMapping("/api/payments/me")
    ResponseEntity<PaymentUseCase.Mine> mine(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return payments.mine(learner).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
