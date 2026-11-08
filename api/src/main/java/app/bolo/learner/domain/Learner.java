package app.bolo.learner.domain;

import app.bolo.common.id.LearnerId;

import java.time.Instant;

public record Learner(LearnerId id, LearnerKind kind, Instant createdAt) {

    public static Learner newAccount(Instant now) {
        return new Learner(LearnerId.newId(), LearnerKind.ACCOUNT, now);
    }

    public static Learner newGuest(Instant now) {
        return new Learner(LearnerId.newId(), LearnerKind.GUEST, now);
    }
}
