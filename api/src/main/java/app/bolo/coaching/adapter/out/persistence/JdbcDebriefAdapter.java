package app.bolo.coaching.adapter.out.persistence;

import app.bolo.coaching.application.port.out.DebriefPort;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcDebriefAdapter implements DebriefPort {

    private final JdbcClient jdbc;

    @Override
    public void start(TalkSessionId talk, Instant at) {
        jdbc.sql("""
                insert into debrief (session_id, status, attempts, updated_at) values (?, 'scoring', 0, ?)
                on conflict (session_id) do update set status = 'scoring', body = null, updated_at = excluded.updated_at""")
                .params(talk.value(), Timestamp.from(at))
                .update();
    }

    @Override
    public void delete(TalkSessionId talk) {
        jdbc.sql("delete from debrief where session_id = ?").param(talk.value()).update();
    }

    @Override
    public void save(TalkSessionId talk, String status, String body, int attempts, Instant at) {
        jdbc.sql("update debrief set status = ?, body = cast(? as jsonb), attempts = ?, updated_at = ? where session_id = ?")
                .params(status, body, attempts, Timestamp.from(at), talk.value())
                .update();
    }

    @Override
    public Optional<Last> latestFor(LearnerId learner) {
        return jdbc.sql("""
                select d.session_id, d.status, d.viewed_at is not null as viewed, d.body::text as body
                from debrief d join talk_session s on s.id = d.session_id
                where s.learner_id = ? order by s.started_at desc limit 1""")
                .param(learner.value())
                .query((rs, n) -> new Last(rs.getObject("session_id", UUID.class).toString(), rs.getString("status"),
                        rs.getBoolean("viewed"), rs.getString("body")))
                .optional();
    }

    @Override
    public java.util.List<Past> history(LearnerId learner) {
        return jdbc.sql("""
                select d.session_id, s.started_at, s.minutes, d.status, d.body::text as body
                from debrief d join talk_session s on s.id = d.session_id
                where s.learner_id = ? order by s.started_at""")
                .param(learner.value())
                .query((rs, n) -> new Past(rs.getObject("session_id", UUID.class).toString(), rs.getTimestamp("started_at").toInstant(),
                        rs.getInt("minutes"), rs.getString("status"), rs.getString("body")))
                .list();
    }

    @Override
    public boolean markViewed(LearnerId learner, TalkSessionId talk, Instant at) {
        return jdbc.sql("""
                update debrief d set viewed_at = coalesce(d.viewed_at, ?)
                from talk_session s where s.id = d.session_id and d.session_id = ? and s.learner_id = ? and d.status = 'ready'""")
                .params(Timestamp.from(at), talk.value(), learner.value()).update() == 1;
    }

    @Override
    public Optional<Stored> find(TalkSessionId talk) {
        return jdbc.sql("""
                select s.learner_id, d.status, d.body::text as body
                from debrief d join talk_session s on s.id = d.session_id where d.session_id = ?""")
                .param(talk.value())
                .query((rs, n) -> new Stored(new LearnerId(rs.getObject("learner_id", UUID.class)), rs.getString("status"),
                        rs.getString("body")))
                .optional();
    }

    @Override
    public int readyCountFor(LearnerId learner, TalkSessionId except) {
        return jdbc.sql("""
                select count(*) from debrief d join talk_session s on s.id = d.session_id
                where s.learner_id = ? and d.status = 'ready' and d.session_id <> ?""")
                .params(learner.value(), except.value())
                .query(Integer.class).single();
    }

    @Override
    public void logDispute(TalkSessionId talk, int fixIndex, String kind, Instant at) {
        jdbc.sql("insert into fix_dispute (session_id, fix_index, kind, at) values (?, ?, ?, ?)")
                .params(talk.value(), fixIndex, kind, Timestamp.from(at))
                .update();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String updateReadyBody(TalkSessionId talk, java.util.function.UnaryOperator<String> change, Instant at) {
        String body = jdbc.sql("select body::text from debrief where session_id = ? and status = 'ready' for update")
                .param(talk.value()).query(String.class).optional().orElse(null);
        if (body == null) return null;
        String next = change.apply(body);
        if (next != null) {
            jdbc.sql("update debrief set body = cast(? as jsonb), updated_at = ? where session_id = ?")
                    .params(next, Timestamp.from(at), talk.value()).update();
        }
        return next;
    }

    @Override
    public void failAllScoring(Instant at) {
        jdbc.sql("update debrief set status = 'failed', updated_at = ? where status = 'scoring'").param(Timestamp.from(at)).update();
    }
}
