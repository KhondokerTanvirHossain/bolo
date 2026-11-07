package app.bolo.common.usage;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** The usage ledger (NFR-22). unitType is tokens_in, tokens_out, audio_ms or chars. */
public interface UsageRecorder {

    void record(LearnerId learner, TalkSessionId session, String vendor, String kind, long units, String unitType);
}
