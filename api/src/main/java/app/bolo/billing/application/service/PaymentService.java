package app.bolo.billing.application.service;

import app.bolo.billing.application.port.in.PaymentUseCase;
import app.bolo.billing.application.port.out.PaymentPort;
import app.bolo.common.error.RefusedException;
import app.bolo.common.error.TooManyRequestsException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.talk.application.port.in.PlansUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentUseCase {

    static final String BKASH_NUMBER = "bkash_number";
    static final Pattern NUMBER = Pattern.compile("01[3-9]\\d{8}");
    static final Pattern TRX = Pattern.compile("[A-Z0-9]{8,12}");

    private final PaymentPort port;
    private final CurrentLearnerUseCase learners;
    private final PlansUseCase plans;
    private final BoloClock clock;

    @Override
    public Optional<String> bkashNumber() {
        return port.setting(BKASH_NUMBER).filter(s -> !s.isBlank());
    }

    @Override
    public void setBkashNumber(String numberOrEmpty) {
        String n = numberOrEmpty == null ? "" : numberOrEmpty.strip();
        if (!n.isEmpty() && !NUMBER.matcher(n).matches()) throw new RefusedException("bad-bkash");
        port.setSetting(BKASH_NUMBER, n);
    }

    @Override
    public void submit(LearnerId learner, String plan, String trxId, String payerNumber) {
        if (!"account".equals(learners.view(learner).kind())) throw new RefusedException("sign-in");
        if (bkashNumber().isEmpty()) throw new RefusedException("bkash-off");
        String trx = trxId == null ? "" : trxId.strip().toUpperCase(java.util.Locale.ROOT);
        String payer = payerNumber == null ? "" : payerNumber.strip();
        var price = plans.plans().stream().filter(p -> p.plan().equals(plan)).findFirst();
        if (price.isEmpty() || !TRX.matcher(trx).matches() || !NUMBER.matcher(payer).matches()) throw new RefusedException("bad-payment");
        var now = clock.now();
        var dayStart = now.atZone(ReferralService.DHAKA).toLocalDate().atStartOfDay(ReferralService.DHAKA).toInstant();
        if (port.countSince(learner, dayStart) >= PER_DAY) throw new TooManyRequestsException();
        if (port.trxUsed(trx)) throw new RefusedException("trx-used");
        if (port.hasPending(learner)) throw new RefusedException("payment-pending");
        try {
            if (!port.insert(learner, plan, price.get().priceBdt(), trx, payer, now)) throw new RefusedException("trx-used");
        } catch (DataIntegrityViolationException raced) {
            // The unique TrxID or the one-pending index caught a request at the same moment.
            throw new RefusedException(port.trxUsed(trx) ? "trx-used" : "payment-pending");
        }
        log.info("learner {} submitted a bKash payment for {}", learner, plan);
    }

    @Override
    public Optional<Mine> mine(LearnerId learner) {
        return port.latest(learner);
    }

    @Override
    public List<Payment> forAdmin() {
        return port.forAdmin();
    }

    @Override
    @Transactional
    public void approve(LearnerId admin, long id) {
        var d = port.decide(id, "approved", null, admin, clock.now()).orElseThrow(() -> new RefusedException("not-pending"));
        plans.grant(d.learner(), d.plan(), PLAN_DAYS, "bkash", d.trxId());
        log.info("admin {} approved payment {} for learner {}", admin, id, d.learner());
    }

    @Override
    @Transactional
    public void reject(LearnerId admin, long id, String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty() || why.length() > 200) throw new RefusedException("bad-reason");
        var d = port.decide(id, "rejected", why, admin, clock.now()).orElseThrow(() -> new RefusedException("not-pending"));
        log.info("admin {} rejected payment {} for learner {}", admin, id, d.learner());
    }
}
