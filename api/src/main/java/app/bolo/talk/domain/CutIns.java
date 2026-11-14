package app.bolo.talk.domain;

/**
 * SCN-2, US-7.3 (2026-11-15): when a Realistic Standup rehearsal may cut in on the learner. Only in a Realistic standup,
 * never with I stammer (AX-8), never in the first learner turn or the talk's last 30 s, at most 2 a talk and a minute
 * apart, and not in the turn right after a pause. One per talk; not thread-safe (the talk's lock guards it).
 */
public final class CutIns {

    /** afterSpeechMs of the learner's speech in a turn, then a breath of silenceMs ends it. */
    public record Config(long afterSpeechMs, long silenceMs, int max, long gapMs, long notLastMs) {
        public static final Config DEFAULT = new Config(20_000, 400, 2, 60_000, 30_000);
    }

    private final Config config;
    private final boolean on;
    private int used;
    private long lastAt;
    private boolean skipNext;

    private CutIns(Config config, boolean on) {
        this.config = config;
        this.on = on;
    }

    public static CutIns forTalk(String scenario, String difficulty, boolean stammer, Config config) {
        return new CutIns(config, "standup".equals(scenario) && "realistic".equals(difficulty) && !stammer);
    }

    public Config config() {
        return config;
    }

    /** Whether the learner's turn number {@code learnerTurn} (1-based) may be cut in; a skip is spent by asking. */
    public boolean allowed(int learnerTurn, long nowMs, long leftMs) {
        if (skipNext) {
            skipNext = false;
            return false;
        }
        return on && learnerTurn > 1 && used < config.max() && leftMs >= config.notLastMs()
                && (used == 0 || nowMs - lastAt >= config.gapMs());
    }

    public void used(long nowMs) {
        used++;
        lastAt = nowMs;
    }

    /** After a pause, the next turn is left alone. */
    public void skipNext() {
        skipNext = true;
    }
}
