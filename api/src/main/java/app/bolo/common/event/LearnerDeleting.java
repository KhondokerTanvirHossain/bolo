package app.bolo.common.event;

import app.bolo.common.id.LearnerId;

/**
 * Delete everything (ACC-4, US-8.6): published before the learner's rows are deleted, so each module removes what
 * the database cascade can't reach (audio files). Listeners run in the deleting transaction.
 */
public record LearnerDeleting(LearnerId learner) {
}
