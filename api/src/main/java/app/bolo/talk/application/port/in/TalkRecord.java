package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.speech.application.port.in.ScoredWord;

import java.util.List;

/** A finished talk as other modules may read it. Word times are stream times; streamStartMs is where the turn began. */
public record TalkRecord(LearnerId learner, int minutes, List<Turn> turns) {

    public record Turn(int idx, String speaker, String text, String audioKey, Long streamStartMs, List<ScoredWord> words,
                       Integer latencyMs) {

        public boolean learner() {
            return "learner".equals(speaker);
        }
    }
}
