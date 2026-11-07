package app.bolo.common.id;

/** Who a vendor call is for, so usage lands on the right learner and talk (NFR-22). */
public record TalkContext(LearnerId learnerId, TalkSessionId sessionId) {
}
