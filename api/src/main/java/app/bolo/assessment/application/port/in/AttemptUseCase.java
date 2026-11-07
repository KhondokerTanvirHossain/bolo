package app.bolo.assessment.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.List;

/** "Try it" (US-4.3/4.4) and the practice page: score one read-aloud attempt; list the practice deck. */
public interface AttemptUseCase {

    record Focus(String word, String kind, String title, String tip) {
    }

    /** level: NAILED | CLOSER | ONCE_MORE. heard is false when nothing recognisable was said. */
    record Outcome(String level, boolean heard, List<Focus> focus) {
    }

    record Practice(String key, String title, String example, String tip, List<String> sentences) {
    }

    /** Refused: "no-consent", "bad-audio" (not a 16 kHz mono WAV of at most 10 s), "bad-reference" (empty or over 30 words). */
    Outcome attempt(LearnerId learner, byte[] wav, String reference);

    List<Practice> practice();
}
