package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.RecordConsentUseCase;
import app.bolo.learner.application.port.out.ConsentLedgerPort;
import app.bolo.learner.domain.Consent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConsentService implements RecordConsentUseCase {

    private final ConsentLedgerPort ledger;
    private final BoloClock clock;
    private final String policyVersion;

    public ConsentService(ConsentLedgerPort ledger, BoloClock clock,
                          @Value("${bolo.policy-version}") String policyVersion) {
        this.ledger = ledger;
        this.clock = clock;
        this.policyVersion = policyVersion;
    }

    @Override
    public void record(LearnerId learner, boolean keepRecordings, boolean over13, String version) {
        if (!over13) throw new RefusedException("under-13");
        if (!policyVersion.equals(version)) throw new RefusedException("policy-version");
        ledger.append(new Consent(learner, keepRecordings, version, clock.now()));
    }
}
