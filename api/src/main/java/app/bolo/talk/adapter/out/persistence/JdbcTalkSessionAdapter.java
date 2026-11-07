package app.bolo.talk.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.application.port.out.TalkSessionPort;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.TalkSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcTalkSessionAdapter implements TalkSessionPort {

    /** Serialises "count live talks, then insert" across concurrent starts. */
    private static final long START_LOCK = 0x7a1c_0001L;

    private final JdbcClient jdbc;

    @Override
    @Transactional
    public boolean insertIfBelowCap(TalkSession s, int cap, Instant liveSince, Instant unattachedSince) {
        jdbc.sql("select pg_advisory_xact_lock(?)").param(START_LOCK).query().singleRow();
        return jdbc.sql("""
                insert into talk_session (id, learner_id, minutes, started_at)
                select ?, ?, ?, ?
                where (select count(*) from talk_session
                       where ended_at is null and started_at > ?
                         and (attached_at is not null or started_at > ?)) < ?""")
                .params(s.id().value(), s.learnerId().value(), s.minutes(), Timestamp.from(s.startedAt()),
                        Timestamp.from(liveSince), Timestamp.from(unattachedSince), cap)
                .update() == 1;
    }

    @Override
    public boolean attach(TalkSessionId id, LearnerId learner, Instant unattachedSince, Instant at) {
        return jdbc.sql("""
                update talk_session set attached_at = ?
                where id = ? and learner_id = ? and ended_at is null and attached_at is null and started_at > ?""")
                .params(Timestamp.from(at), id.value(), learner.value(), Timestamp.from(unattachedSince))
                .update() == 1;
    }

    @Override
    public void endOpenFor(LearnerId learner, EndReason reason, Instant at) {
        jdbc.sql("update talk_session set ended_at = ?, end_reason = ? where learner_id = ? and ended_at is null")
                .params(Timestamp.from(at), reason.dbValue(), learner.value())
                .update();
    }

    @Override
    public void endAllOpen(EndReason reason, Instant at) {
        jdbc.sql("update talk_session set ended_at = ?, end_reason = ? where ended_at is null")
                .params(Timestamp.from(at), reason.dbValue())
                .update();
    }

    @Override
    public Optional<TalkSession> findLive(TalkSessionId id, LearnerId learner) {
        return jdbc.sql("""
                select id, learner_id, minutes, started_at from talk_session
                where id = ? and learner_id = ? and ended_at is null""")
                .params(id.value(), learner.value())
                .query((rs, n) -> new TalkSession(new TalkSessionId(rs.getObject("id", UUID.class)),
                        new LearnerId(rs.getObject("learner_id", UUID.class)), rs.getInt("minutes"),
                        rs.getTimestamp("started_at").toInstant(), null))
                .optional();
    }

    @Override
    public void end(TalkSessionId id, EndReason reason, Instant at) {
        jdbc.sql("update talk_session set ended_at = ?, end_reason = ? where id = ? and ended_at is null")
                .params(Timestamp.from(at), reason.dbValue(), id.value())
                .update();
    }

    @Override
    public List<TalkSessionId> audioDue(Instant noConsentEndedBefore, Instant anyEndedBefore) {
        return jdbc.sql("""
                select s.id from talk_session s
                where s.ended_at is not null and s.media_purged_at is null
                  and (s.ended_at < ?
                       or (s.ended_at < ? and not coalesce((select c.keep_recordings from consent_record c
                                                            where c.learner_id = s.learner_id
                                                            order by c.id desc limit 1), false)))""")
                .params(Timestamp.from(anyEndedBefore), Timestamp.from(noConsentEndedBefore))
                .query((rs, n) -> new TalkSessionId(rs.getObject("id", UUID.class)))
                .list();
    }

    @Override
    public void markAudioPurged(TalkSessionId id, Instant at) {
        jdbc.sql("update talk_session set media_purged_at = ? where id = ?").params(Timestamp.from(at), id.value()).update();
    }
}
