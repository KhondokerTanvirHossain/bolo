package app.bolo.learner.application.service;

import app.bolo.common.error.TooManyRequestsException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.learner.application.port.in.LearnerView;
import app.bolo.learner.application.port.in.StartGuestUseCase;
import app.bolo.learner.application.port.out.AccountPort;
import app.bolo.learner.application.port.out.ConsentLedgerPort;
import app.bolo.learner.application.port.out.LearnerPort;
import app.bolo.learner.application.port.out.ProfilePort;
import app.bolo.learner.application.port.out.SessionPort;
import app.bolo.learner.application.port.out.SettingsPort;
import app.bolo.learner.domain.Settings;
import app.bolo.learner.domain.Learner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
public class GuestService implements StartGuestUseCase, CurrentLearnerUseCase {

    /** NFR-17: sessions expire after 90 days of inactivity. */
    static final Duration IDLE_EXPIRY = Duration.ofDays(90);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LearnerPort learners;
    private final ConsentLedgerPort consents;
    private final SessionPort sessions;
    private final ProfilePort profiles;
    private final SettingsPort settings;
    private final AccountPort accounts;
    private final BoloClock clock;
    private final GuestRateLimiter newGuests;
    private final InviteGate inviteGate;

    public GuestService(LearnerPort learners, ConsentLedgerPort consents, SessionPort sessions, ProfilePort profiles,
                        SettingsPort settings, AccountPort accounts, InviteGate inviteGate, BoloClock clock, @Value("${bolo.guests-per-ip-per-hour}") int guestsPerIpPerHour,
                        @Value("${bolo.guests-per-hour}") int guestsPerHour) {
        this.learners = learners;
        this.consents = consents;
        this.sessions = sessions;
        this.profiles = profiles;
        this.settings = settings;
        this.accounts = accounts;
        this.inviteGate = inviteGate;
        this.clock = clock;
        this.newGuests = new GuestRateLimiter(guestsPerIpPerHour, guestsPerHour, Duration.ofHours(1));
    }

    @Override
    @Transactional
    public Started start(String existingTokenOrNull, String clientKey, String invite) {
        if (existingTokenOrNull != null) {
            Optional<LearnerId> known = byToken(existingTokenOrNull);
            if (known.isPresent()) return new Started(view(known.get()), null);
        }
        if (!newGuests.tryAcquire(clientKey, clock.now())) throw new TooManyRequestsException();
        boolean unlimited = inviteGate.admitNewcomer(invite);
        Learner guest = Learner.newGuest(clock.now());
        learners.save(guest);
        if (unlimited) learners.setUnlimited(guest.id());
        String token = newToken();
        sessions.create(hash(token), guest.id(), clock.now());
        return new Started(view(guest.id()), token);
    }

    @Override
    public Optional<LearnerId> byToken(String token) {
        return sessions.touch(hash(token), clock.now().minus(IDLE_EXPIRY), clock.now());
    }

    @Override
    public LearnerView view(LearnerId id) {
        Learner learner = learners.find(id).orElseThrow();
        LearnerView.ConsentView consent = consents.latest(id)
                .map(c -> new LearnerView.ConsentView(c.keepRecordings(), c.policyVersion()))
                .orElse(null);
        LearnerView.ProfileView profile = profiles.find(id)
                .map(p -> new LearnerView.ProfileView(p.goal().key(), p.fearedMoment().key(), p.talkMinutes()))
                .orElse(null);
        Settings s = settings.find(id).orElse(Settings.DEFAULTS);
        LearnerView.AccountView account = accounts.find(id).map(a -> new LearnerView.AccountView(a.email(), a.name())).orElse(null);
        return new LearnerView(id, learner.kind().dbValue(), consent, profile, new LearnerView.SettingsView(s.buddySpeed(), s.captions(), s.talkView()),
                account, learners.unlimited(id));
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
