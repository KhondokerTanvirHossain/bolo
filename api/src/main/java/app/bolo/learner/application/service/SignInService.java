package app.bolo.learner.application.service;

import app.bolo.common.error.SignInFailedException;
import app.bolo.common.event.LearnerMerged;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.learner.application.port.in.SignInUseCase;
import app.bolo.learner.application.port.out.AccountPort;
import app.bolo.learner.application.port.out.IdentityPort;
import app.bolo.learner.application.port.out.LearnerMergePort;
import app.bolo.learner.application.port.out.LearnerPort;
import app.bolo.learner.application.port.out.SessionPort;
import app.bolo.learner.domain.Learner;
import app.bolo.learner.domain.LearnerKind;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class SignInService implements SignInUseCase {

    private final IdentityPort identities;
    private final AccountPort accounts;
    private final LearnerPort learners;
    private final SessionPort sessions;
    private final LearnerMergePort merge;
    private final CurrentLearnerUseCase current;
    private final ApplicationEventPublisher events;
    private final BoloClock clock;
    private final TransactionTemplate tx;
    private final InviteGate inviteGate;

    public SignInService(IdentityPort identities, AccountPort accounts, LearnerPort learners, SessionPort sessions,
                         LearnerMergePort merge, CurrentLearnerUseCase current, ApplicationEventPublisher events, BoloClock clock,
                         PlatformTransactionManager transactions, InviteGate inviteGate) {
        this.inviteGate = inviteGate;
        this.identities = identities;
        this.accounts = accounts;
        this.learners = learners;
        this.sessions = sessions;
        this.merge = merge;
        this.current = current;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public SignedIn signIn(String currentTokenOrNull, String idToken, String inviteOrNull) {
        IdentityPort.VerifiedIdentity who = identities.verify(idToken).orElseThrow(SignInFailedException::new);
        try {
            return tx.execute(s -> once(currentTokenOrNull, who, inviteOrNull));
        } catch (DataIntegrityViolationException raced) {
            // Another sign-in for the same Google user made the account first: sign into that one.
            return tx.execute(s -> once(currentTokenOrNull, who, inviteOrNull));
        }
    }

    private SignedIn once(String currentToken, IdentityPort.VerifiedIdentity who, String invite) {
        Optional<LearnerId> now = currentToken == null ? Optional.empty() : current.byToken(currentToken);
        Optional<LearnerId> existing = accounts.learnerFor(who.uid());
        LearnerId target;
        if (existing.isPresent()) {
            target = existing.get();
            now.filter(g -> !g.equals(target)).filter(this::isGuest).ifPresent(guest -> {
                merge.moveIfAccountHasNone(guest, target);
                events.publishEvent(new LearnerMerged(guest, target));
                learners.delete(guest);
            });
            accounts.signedIn(target, who.email(), who.name(), clock.now());
        } else if (now.isPresent() && isGuest(now.get())) {
            target = now.get();
            accounts.insert(target, who.uid(), who.email(), who.name(), clock.now());
            learners.setKind(target, LearnerKind.ACCOUNT);
        } else {
            inviteGate.admitNewcomer(invite);
            Learner fresh = Learner.newAccount(clock.now());
            learners.save(fresh);
            accounts.insert(fresh.id(), who.uid(), who.email(), who.name(), clock.now());
            target = fresh.id();
        }
        if (currentToken != null) sessions.delete(GuestService.hash(currentToken));
        String token = GuestService.newToken();
        sessions.create(GuestService.hash(token), target, clock.now());
        return new SignedIn(current.view(target), token);
    }

    private boolean isGuest(LearnerId id) {
        return learners.find(id).map(l -> l.kind() == LearnerKind.GUEST).orElse(false);
    }

    @Override
    public void signOut(String tokenOrNull) {
        if (tokenOrNull != null) sessions.delete(GuestService.hash(tokenOrNull));
    }
}
