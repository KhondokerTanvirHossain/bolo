package app.bolo.assessment.adapter.out.persistence;

import app.bolo.assessment.application.port.out.AttemptPort;
import app.bolo.common.id.LearnerId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
class JdbcAttemptAdapter implements AttemptPort {

    private final JdbcClient jdbc;

    @Override
    public void save(LearnerId learner, String reference, String level, boolean heard, double accuracy, String focusJson, Instant at) {
        jdbc.sql("""
                insert into attempt (learner_id, reference, level, heard, accuracy, focus, at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), ?)""")
                .params(learner.value(), reference, level, heard, accuracy, focusJson, Timestamp.from(at))
                .update();
    }
}
