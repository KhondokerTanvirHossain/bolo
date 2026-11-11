package app.bolo.billing.application.service;

import app.bolo.billing.application.port.in.ReferralUseCase;
import app.bolo.billing.application.port.out.ReferralPort;
import app.bolo.common.error.RefusedException;
import app.bolo.common.event.AccountCreated;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.learner.application.port.in.LearnerView;
import app.bolo.talk.application.port.in.FreeTalksUseCase;
import app.bolo.talk.application.port.in.PlansUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ReferralService implements ReferralUseCase {

    static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final Pattern CODE = Pattern.compile("[a-z0-9]{8}");
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralPort port;
    private final CurrentLearnerUseCase learners;
    private final FreeTalksUseCase talks;
    private final PlansUseCase plans;
    private final BoloClock clock;
    private final LocalDate until;

    public ReferralService(ReferralPort port, CurrentLearnerUseCase learners, FreeTalksUseCase talks, PlansUseCase plans,
                           BoloClock clock, @Value("${bolo.referral.until:2027-11-30}") LocalDate until) {
        this.port = port;
        this.learners = learners;
        this.talks = talks;
        this.plans = plans;
        this.clock = clock;
        this.until = until;
    }

    boolean offerRuns() {
        return !clock.now().atZone(DHAKA).toLocalDate().isAfter(until);
    }

    /** A new account that came through a friend's link, while the offer runs. Never fails the sign-in. */
    @EventListener
    public void onAccountCreated(AccountCreated e) {
        if (e.ref() == null || !CODE.matcher(e.ref()).matches() || !offerRuns()) return;
        try {
            port.learnerForCode(e.ref()).filter(referrer -> !referrer.equals(e.learner()))
                    .ifPresent(referrer -> port.insertReferral(e.learner(), referrer, clock.now()));
        } catch (RuntimeException ex) {
            log.warn("referral not recorded: {}", ex.getClass().getSimpleName());
        }
    }

    @Override
    @Transactional
    public Status status(LearnerId learner) {
        LearnerView me = learners.view(learner);
        if (!"account".equals(me.kind())) throw new RefusedException("sign-in");
        String code = codeFor(learner);
        List<String> names = new ArrayList<>();
        for (LearnerId friend : port.friends(learner)) {
            var h = talks.history(friend);
            if (h.counted() > 0 && !h.returning()) names.add(firstName(learners.view(friend)));
        }
        int earned = Math.min(MAX_REWARDS, names.size() / FRIENDS_PER_REWARD);
        var now = clock.now();
        for (int n = port.rewards(learner).size() + 1; n <= earned; n++) {
            // The row is the guard: only the request that adds reward n grants it.
            if (port.insertReward(learner, n, now)) {
                port.setRewardEnd(learner, n, plans.grant(learner, "premium", REWARD_DAYS, "referral", "invite reward " + n));
                log.info("learner {} earned referral reward {}", learner, n);
            }
        }
        var rewards = port.rewards(learner);
        Instant until = rewards.stream().map(ReferralPort.Reward::endsAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        boolean fresh = rewards.stream().anyMatch(r -> r.seenAt() == null);
        return new Status(offerRuns(), code, names, names.size(), rewards.size(), MAX_REWARDS, until, fresh);
    }

    @Override
    public void seen(LearnerId learner) {
        port.seen(learner, clock.now());
    }

    private String codeFor(LearnerId learner) {
        var existing = port.code(learner);
        if (existing.isPresent()) return existing.get();
        for (int i = 0; i < 5; i++) {
            StringBuilder b = new StringBuilder(8);
            for (int j = 0; j < 8; j++) b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            if (port.insertCode(learner, b.toString(), clock.now())) return port.code(learner).orElseThrow();
        }
        throw new IllegalStateException("no free referral code");
    }

    private static String firstName(LearnerView v) {
        String name = v.account() == null ? null : v.account().name();
        if (name == null || name.isBlank()) return "A friend";
        return name.strip().split("\\s+")[0];
    }
}
