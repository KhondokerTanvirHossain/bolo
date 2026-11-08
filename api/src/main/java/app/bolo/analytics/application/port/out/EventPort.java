package app.bolo.analytics.application.port.out;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.Map;

public interface EventPort {

    void insert(LearnerId learner, String name, Map<String, Object> props, Instant at);
}
