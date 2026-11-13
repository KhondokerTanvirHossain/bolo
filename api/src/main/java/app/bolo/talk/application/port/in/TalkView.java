package app.bolo.talk.application.port.in;

import app.bolo.common.id.TalkSessionId;

/** A live talk as the relay and the web client know it. goal/fearedMoment are null without a profile. */
/** {@code slowVoice}: the learner chose the slower Bolo in Settings (SET-1); fixed for this talk. */
/** P3c (SET-5, SET-6): {@code voice} is Bolo's voice key ("british-female"), {@code mood} Bolo's mood; fixed for this talk. */
/** AX-7, AX-8 (2026-11-14): {@code answerTime} the resolved pace ("quick" | "normal" | "relaxed", never "auto") and
 * {@code stammer}; fixed for this talk. */
public record TalkView(TalkSessionId id, int minutes, String goal, String fearedMoment, boolean slowVoice, String voice, String mood,
                       String answerTime, boolean stammer) {

    public TalkView(TalkSessionId id, int minutes, String goal, String fearedMoment, boolean slowVoice) {
        this(id, minutes, goal, fearedMoment, slowVoice, null, null);
    }

    public TalkView(TalkSessionId id, int minutes, String goal, String fearedMoment, boolean slowVoice, String voice, String mood) {
        this(id, minutes, goal, fearedMoment, slowVoice, voice, mood, "normal", false);
    }
}
