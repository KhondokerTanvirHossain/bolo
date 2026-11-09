package app.bolo.learner.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.VoiceIntroUseCase.Recording;
import app.bolo.learner.application.port.out.VoiceIntroPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
class JdbcVoiceIntroAdapter implements VoiceIntroPort {

    private final JdbcClient jdbc;

    @Override
    public void save(LearnerId learner, int day, Instant at, boolean onServer) {
        jdbc.sql("""
                insert into voice_intro (learner_id, day, recorded_at, on_server) values (?, ?, ?, ?)
                on conflict (learner_id, day) do update set recorded_at = excluded.recorded_at, on_server = excluded.on_server""")
                .params(learner.value(), day, Timestamp.from(at), onServer).update();
    }

    @Override
    public List<Due> serverAudioOlderThan(Instant before) {
        return jdbc.sql("select learner_id, day from voice_intro where on_server and recorded_at < ?").param(Timestamp.from(before))
                .query((rs, n) -> new Due(new LearnerId(rs.getObject("learner_id", java.util.UUID.class)), rs.getInt("day"))).list();
    }

    @Override
    public void markOffServer(LearnerId learner, int day) {
        jdbc.sql("update voice_intro set on_server = false where learner_id = ? and day = ?").params(learner.value(), day).update();
    }

    @Override
    public List<Recording> list(LearnerId learner) {
        return jdbc.sql("select day, recorded_at, on_server from voice_intro where learner_id = ? order by day")
                .param(learner.value())
                .query((rs, n) -> new Recording(rs.getInt("day"), rs.getTimestamp("recorded_at").toInstant(), rs.getBoolean("on_server")))
                .list();
    }
}
