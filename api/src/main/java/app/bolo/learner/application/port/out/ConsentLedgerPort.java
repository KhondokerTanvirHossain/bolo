package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Consent;

import java.util.Optional;

public interface ConsentLedgerPort {

    void append(Consent consent);

    Optional<Consent> latest(LearnerId learner);
}
