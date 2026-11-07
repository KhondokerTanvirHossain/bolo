package app.bolo.coaching.domain;

import java.util.List;

/** What the Coach model returns (structured output); validated by {@link ReplyCheck} before anything is shown. */
public record CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel) {

    public record Strength(int turn, String quote, String text) {
    }

    public record Upgrade(int turn, String said, String better) {
    }
}
