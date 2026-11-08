package app.bolo.analytics.adapter.out.persistence;

import app.bolo.analytics.application.port.out.EventPort;
import app.bolo.common.id.LearnerId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
class JdbcEventAdapter implements EventPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcClient jdbc;

    @Override
    public void insert(LearnerId learner, String name, Map<String, Object> props, Instant at) {
        jdbc.sql("insert into event (learner_id, name, props, at) values (?, ?, ?::jsonb, ?)")
                .params(learner.value(), name, JSON.writeValueAsString(props), Timestamp.from(at)).update();
    }

    @Override
    public void moveLearner(LearnerId from, LearnerId to) {
        jdbc.sql("update event set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
    }
}
