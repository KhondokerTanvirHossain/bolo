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
    public int startsBy(LearnerId learner, Instant since) {
        return jdbc.sql("select count(*) from talk_session where learner_id = ? and started_at >= ?")
                .params(learner.value(), Timestamp.from(since)).query(Integer.class).single();
    }

    @Override
    public void lockStarts() {
        jdbc.sql("select pg_advisory_xact_lock(?)").param(START_LOCK).query().singleRow();
    }

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
    public boolean anyAttachedLive(Instant liveSince) {
        return jdbc.sql("select exists (select 1 from talk_session where ended_at is null and attached_at is not null and started_at > ?)")
                .param(Timestamp.from(liveSince)).query(Boolean.class).single();
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
                  and (s.ended_at < ? or (s.ended_at < ? and not coalesce(s.keep_audio, false)))""")
                .params(Timestamp.from(anyEndedBefore), Timestamp.from(noConsentEndedBefore))
                .query((rs, n) -> new TalkSessionId(rs.getObject("id", UUID.class)))
                .list();
    }

    @Override
    public void markAudioPurged(TalkSessionId id, Instant at) {
        jdbc.sql("update talk_session set media_purged_at = ? where id = ?").params(Timestamp.from(at), id.value()).update();
    }

    @Override
    public java.util.List<app.bolo.common.id.TalkSessionId> idsFor(LearnerId learner) {
        return jdbc.sql("select id from talk_session where learner_id = ?").param(learner.value())
                .query((rs, n) -> new app.bolo.common.id.TalkSessionId(rs.getObject("id", java.util.UUID.class))).list();
    }

    @Override
    public void recordKeepChoice(app.bolo.common.id.TalkSessionId talk, boolean keep) {
        jdbc.sql("update talk_session set keep_audio = ? where id = ?").params(keep, talk.value()).update();
    }

    @Override
    public java.util.Set<app.bolo.common.id.TalkSessionId> existing(java.util.Collection<app.bolo.common.id.TalkSessionId> ids) {
        if (ids.isEmpty()) return java.util.Set.of();
        java.util.UUID[] arr = ids.stream().map(app.bolo.common.id.TalkSessionId::value).toArray(java.util.UUID[]::new);
        return new java.util.HashSet<>(jdbc.sql("select id from talk_session where id = any(?)").param(arr)
                .query((rs, n) -> new app.bolo.common.id.TalkSessionId(rs.getObject("id", java.util.UUID.class))).list());
    }

    /** The free-plan counting rule (see {@link TalkSessionPort#countedStarts}). */
    private static final String COUNTED = """
            select s.started_at from talk_session s left join debrief d on d.session_id = s.id
            where s.learner_id = ? and s.attached_at is not null and coalesce(d.status, '') <> 'failed'
              and (s.ended_at is null or d.status in ('scoring', 'ready')
                   or s.ended_at - s.attached_at >= interval '90 seconds')""";

    @Override
    public java.util.List<Instant> countedStarts(LearnerId learner) {
        return jdbc.sql(COUNTED).param(learner.value()).query((rs, n) -> rs.getTimestamp("started_at").toInstant()).list();
    }

    @Override
    public void rememberFreeTalks(LearnerId learner, String key) {
        jdbc.sql("insert into free_talk_use (subject_hash, started_at) select ?, c.started_at from (" + COUNTED + ") c "
                        + "on conflict do nothing").params(key, learner.value()).update();
    }

    @Override
    public java.util.List<Instant> rememberedStarts(String key) {
        return jdbc.sql("select started_at from free_talk_use where subject_hash = ?").param(key)
                .query((rs, n) -> rs.getTimestamp("started_at").toInstant()).list();
    }

    @Override
    public void forgetFreeTalkUseBefore(Instant before) {
        jdbc.sql("delete from free_talk_use where started_at < ?").param(Timestamp.from(before)).update();
    }

    @Override
    public void moveLearner(LearnerId from, LearnerId to) {
        jdbc.sql("update talk_session set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
    }
}
