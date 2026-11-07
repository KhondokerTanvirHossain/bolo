package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

public interface RecordConsentUseCase {

    /** Appends a consent row. Refuses with code "under-13" or "policy-version". */
    void record(LearnerId learner, boolean keepRecordings, boolean over13, String policyVersion);
}
