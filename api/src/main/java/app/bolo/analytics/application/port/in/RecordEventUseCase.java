package app.bolo.analytics.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.Map;

public interface RecordEventUseCase {

    /** A browser event. Refuses with "event" (unknown or bad props); too many in a minute is "slow-down". */
    void record(LearnerId learner, String name, Map<String, Object> props);
}
