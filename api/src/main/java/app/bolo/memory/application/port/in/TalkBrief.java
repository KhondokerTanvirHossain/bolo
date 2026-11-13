package app.bolo.memory.application.port.in;

/**
 * What Bolo knows before a talk opens (P3b): a block for Bolo's instructions. {@code followUp}: the block opens the talk
 * with one personal follow-up, so Bolo shouldn't open from the goal instead. {@code known}: the block carries notes about
 * the learner, so with no follow-up Bolo opens from them, not with a first-talk question (review, P3b).
 */
public record TalkBrief(String text, boolean followUp, boolean known) {

    public static final TalkBrief NONE = new TalkBrief("", false, false);
}
