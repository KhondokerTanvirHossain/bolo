package app.bolo.talk.domain;

/**
 * TLK-6 silence help, counted over the learner's turn time and carried across Bolo's re-prompt: encourage at 8 s,
 * a hint card at 15 s, pause at 45 s — each once — until the learner speaks again.
 */
public class QuietClock {

    public enum Stage { NONE, ENCOURAGE, HINT, PAUSE }

    private final long encourageMs;
    private final long hintMs;
    private final long pauseMs;
    private long quietMs;
    private Stage reached = Stage.NONE;

    public QuietClock(long encourageMs, long hintMs, long pauseMs) {
        this.encourageMs = encourageMs;
        this.hintMs = hintMs;
        this.pauseMs = pauseMs;
    }

    public QuietClock(Pace pace) {
        this(pace.encourageMs(), pace.hintMs(), pace.pauseMs());
    }

    /** The silent turn time counted since the learner last spoke (or since the clock started). */
    public long quietMs() {
        return quietMs;
    }

    public Stage onQuiet(long ms) {
        quietMs += ms;
        Stage next = quietMs >= pauseMs ? Stage.PAUSE : quietMs >= hintMs ? Stage.HINT : quietMs >= encourageMs ? Stage.ENCOURAGE : Stage.NONE;
        if (next.ordinal() <= reached.ordinal()) return Stage.NONE;
        reached = next;
        return next;
    }

    public void onSpeech() {
        quietMs = 0;
        reached = Stage.NONE;
    }
}
