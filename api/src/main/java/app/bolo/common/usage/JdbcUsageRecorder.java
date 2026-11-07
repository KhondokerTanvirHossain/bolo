package app.bolo.common.usage;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
class JdbcUsageRecorder implements UsageRecorder {

    private final JdbcClient jdbc;
    private final BoloClock clock;

    @Override
    public void record(LearnerId learner, TalkSessionId session, String vendor, String kind, long units, String unitType) {
        if (units <= 0) return;
        jdbc.sql("""
                insert into usage (learner_id, session_id, vendor, kind, units, unit_type, at)
                values (?, ?, ?, ?, ?, ?, ?)""")
                .params(learner.value(), session == null ? null : session.value(), vendor, kind, units, unitType,
                        Timestamp.from(clock.now()))
                .update();
    }
}
