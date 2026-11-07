package app.bolo.talk.application.port.out;

import app.bolo.common.id.TalkSessionId;
import app.bolo.speech.application.port.in.ScoredWord;

import app.bolo.talk.application.port.in.TalkRecord;

import java.util.List;
import java.util.Optional;

public interface TurnPort {

    /** speaker is "buddy" or "learner"; serverMs and heardMs only for Buddy replies. */
    void save(TalkSessionId session, int idx, String speaker, String text, Integer serverMs, Integer heardMs);

    void heard(TalkSessionId session, int idx, int ms);

    /** A learner turn with its audio (key into the audio store, may be null), stream start and scored words. */
    void saveLearner(TalkSessionId session, int idx, String text, String audioKey, long streamStartMs, List<ScoredWord> words);

    /** Scores that arrived after the turn was saved (the recogniser's last segment can lag the turn end). */
    void updateScored(TalkSessionId session, int idx, List<ScoredWord> words);

    Optional<TalkRecord> record(TalkSessionId session);
}
