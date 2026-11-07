package app.bolo.talk.domain;

import java.util.Locale;

/**
 * Decides when the learner's turn is over (TLK-4), frame by frame. Silence is timed from the first silent frame
 * after speech. Silence alone never ends a turn; speech the recogniser made no words of ends as UNCLEAR, so
 * Bolo can kindly ask again (TLK-7) instead of waiting forever.
 */
public class TurnTaker {

    public enum Decision { NONE, SPECULATE, RESUMED, COMMIT, UNCLEAR }

    private final TurnPolicy policy;

    private String transcript = "";
    private boolean heardSpeech;
    private long silenceStartMs = -1;
    private long speechMs;
    private long lastFrameMs = -1;
    /** Wordless sound shorter than this (a cough, a door) is not a turn. */
    static final long MIN_UNCLEAR_SPEECH_MS = 800;
    private boolean speculated;
    private boolean committed;

    public TurnTaker(TurnPolicy policy) {
        this.policy = policy;
    }

    public Decision onFrame(boolean speech, long nowMs) {
        if (committed) return Decision.NONE;
        long frame = lastFrameMs < 0 ? 20 : Math.max(0, nowMs - lastFrameMs);
        lastFrameMs = nowMs;
        if (speech) {
            speechMs += frame;
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
        long silence = nowMs - silenceStartMs;
        if (transcript.isBlank()) {
            // Speech with no words (Bangla, mumbling): end the turn so Bolo can nudge, rather than wait forever.
            if (silence < policy.endAfterMs() || speechMs < MIN_UNCLEAR_SPEECH_MS) return Decision.NONE;
            committed = true;
            return Decision.UNCLEAR;
        }
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
        speechMs = 0;
        lastFrameMs = -1;
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
