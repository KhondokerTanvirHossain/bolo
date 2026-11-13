package app.bolo.memory.application.port.in;

/**
 * What Bolo knows before a talk opens (P3b): a block for Bolo's instructions. {@code followUp}: the block opens the talk
 * with one personal follow-up, so Bolo shouldn't open from the goal instead.
 */
public record TalkBrief(String text, boolean followUp) {

    public static final TalkBrief NONE = new TalkBrief("", false);
}
