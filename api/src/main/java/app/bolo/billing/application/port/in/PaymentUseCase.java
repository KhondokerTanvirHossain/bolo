package app.bolo.billing.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** P2e: bKash Send Money to the founder's number; the learner types the TrxID; the admin approves or rejects. */
public interface PaymentUseCase {

    int PLAN_DAYS = 30;
    int PER_DAY = 5;

    record Mine(String status, String plan, String trxId, String reason) {
    }

    record Payment(long id, Instant at, String email, String plan, int amountBdt, String trxId, String payerNumber,
                   String status, String reason) {
    }

    /** Empty when bKash payments are off (no number set). */
    Optional<String> bkashNumber();

    void setBkashNumber(String numberOrEmpty);

    void submit(LearnerId learner, String plan, String trxId, String payerNumber);

    Optional<Mine> mine(LearnerId learner);

    /** Pending first (all of them), then the latest 50 decided. */
    List<Payment> forAdmin();

    void approve(LearnerId admin, long id);

    void reject(LearnerId admin, long id, String reason);
}
