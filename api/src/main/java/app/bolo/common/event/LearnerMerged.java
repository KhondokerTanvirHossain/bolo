package app.bolo.common.event;

import app.bolo.common.id.LearnerId;

/**
 * A guest signed into an existing account (US-8.1, US-8.4): every module moves the rows it owns from {@code from} to
 * {@code to}. Published inside the sign-in transaction; listeners run in it, and the guest is deleted afterwards.
 */
public record LearnerMerged(LearnerId from, LearnerId to) {
}
