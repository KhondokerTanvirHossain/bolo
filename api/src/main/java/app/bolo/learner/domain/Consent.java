package app.bolo.learner.domain;

import app.bolo.common.id.LearnerId;

import java.time.Instant;

/** One recorded choice. over-13 is not stored as a field: a row exists only if the learner confirmed it. */
public record Consent(LearnerId learnerId, boolean keepRecordings, String policyVersion, Instant recordedAt) {
}
