package app.bolo.talk.domain;

import java.util.Locale;

/**
 * Decides when the learner's turn is over (TLK-4), frame by frame. Silence is timed from the first silent frame
 * after speech. Nothing is decided until the learner has said something the recogniser turned into words, so
 * silence alone or a cough never ends a turn.
 */
public class TurnTaker {

    public enum Decision { NONE, SPECULATE, RESUMED, COMMIT }

    private final TurnPolicy policy;

    private String transcript = "";
    private boolean heardSpeech;
    private long silenceStartMs = -1;
    private boolean speculated;
    private boolean committed;

    public TurnTaker(TurnPolicy policy) {
        this.policy = policy;
    }

    public Decision onFrame(boolean speech, long nowMs) {
        if (committed) return Decision.NONE;
        if (speech) {
            heardSpeech = true;
            silenceStartMs = -1;
            if (speculated) {
                speculated = false;
                return Decision.RESUMED;
            }
            return Decision.NONE;
        }
        if (!heardSpeech) return Decision.NONE;
        if (silenceStartMs < 0) silenceStartMs = nowMs;
        if (transcript.isBlank()) return Decision.NONE;
        long silence = nowMs - silenceStartMs;
        if (silence >= endAfterMs()) {
            committed = true;
            return Decision.COMMIT;
        }
        if (!speculated && silence >= policy.speculateAfterMs()) {
            speculated = true;
            return Decision.SPECULATE;
        }
        return Decision.NONE;
    }

    public void onTranscript(String textSoFar) {
        transcript = textSoFar == null ? "" : textSoFar;
    }

    /** The learner tapped Done: end now, unless nothing has been said. */
    public Decision onDone(long nowMs) {
        if (committed || transcript.isBlank()) return Decision.NONE;
        committed = true;
        return Decision.COMMIT;
    }

    public void reset() {
        transcript = "";
        heardSpeech = false;
        silenceStartMs = -1;
        speculated = false;
        committed = false;
    }

    private long endAfterMs() {
        return soundsUnfinished() ? policy.unfinishedEndAfterMs() : policy.endAfterMs();
    }

    private boolean soundsUnfinished() {
        String[] words = transcript.trim().toLowerCase(Locale.ROOT).split("\\s+");
        String last = words[words.length - 1].replaceAll("[^a-z']", "");
        return policy.unfinishedWords().contains(last);
    }
}
