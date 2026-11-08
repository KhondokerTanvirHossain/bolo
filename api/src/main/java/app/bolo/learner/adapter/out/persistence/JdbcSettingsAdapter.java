package app.bolo.learner.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.out.SettingsPort;
import app.bolo.learner.domain.Settings;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JdbcSettingsAdapter implements SettingsPort {

    private final JdbcClient jdbc;

    @Override
    public void upsert(LearnerId learner, Settings s, Instant now) {
        jdbc.sql("""
                insert into learner_settings (learner_id, buddy_speed, captions, updated_at) values (?, ?, ?, ?)
                on conflict (learner_id) do update set buddy_speed = excluded.buddy_speed, captions = excluded.captions,
                    updated_at = excluded.updated_at""")
                .params(learner.value(), s.buddySpeed(), s.captions(), Timestamp.from(now)).update();
    }

    @Override
    public Optional<Settings> find(LearnerId learner) {
        return jdbc.sql("select buddy_speed, captions from learner_settings where learner_id = ?").param(learner.value())
                .query((rs, n) -> new Settings(rs.getString("buddy_speed"), rs.getBoolean("captions"))).optional();
    }
}
