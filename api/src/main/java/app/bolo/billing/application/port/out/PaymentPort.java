package app.bolo.billing.application.port.out;

import app.bolo.billing.application.port.in.PaymentUseCase.Mine;
import app.bolo.billing.application.port.in.PaymentUseCase.Payment;
import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentPort {

    record Decided(LearnerId learner, String plan, String trxId) {
    }

    Optional<String> setting(String key);

    void setSetting(String key, String value);

    boolean trxUsed(String trxId);

    boolean hasPending(LearnerId learner);

    int countSince(LearnerId learner, Instant since);

    void insert(LearnerId learner, String plan, int amountBdt, String trxId, String payerNumber, Instant at);

    Optional<Mine> latest(LearnerId learner);

    List<Payment> forAdmin();

    /** Only a pending payment changes; empty when it wasn't pending (decided already, or no such payment). */
    Optional<Decided> decide(long id, String status, String reason, LearnerId admin, Instant at);
}
