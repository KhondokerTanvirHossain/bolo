package app.bolo.learner.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.out.ConsentLedgerPort;
import app.bolo.learner.application.port.out.LearnerPort;
import app.bolo.learner.application.port.out.SessionPort;
import app.bolo.learner.domain.Consent;
import app.bolo.learner.domain.Learner;
import app.bolo.learner.domain.LearnerKind;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcLearnerAdapter implements LearnerPort, ConsentLedgerPort, SessionPort {

    private final JdbcClient jdbc;

    @Override
    public void save(Learner learner) {
        jdbc.sql("insert into learner (id, kind, created_at) values (?, ?, ?)")
                .params(learner.id().value(), learner.kind().dbValue(), Timestamp.from(learner.createdAt()))
                .update();
    }

    @Override
    public Optional<Learner> find(LearnerId id) {
        return jdbc.sql("select id, kind, created_at from learner where id = ?")
                .param(id.value())
                .query((rs, n) -> new Learner(new LearnerId(rs.getObject("id", UUID.class)),
                        LearnerKind.fromDbValue(rs.getString("kind")), rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public void setKind(LearnerId id, app.bolo.learner.domain.LearnerKind kind) {
        jdbc.sql("update learner set kind = ? where id = ?").params(kind.dbValue(), id.value()).update();
    }

    @Override
    public void delete(LearnerId id) {
        jdbc.sql("delete from learner where id = ?").param(id.value()).update();
    }

    @Override
    public void delete(byte[] tokenHash) {
        jdbc.sql("delete from web_session where token_hash = ?").param(tokenHash).update();
    }

    @Override
    public void append(Consent c) {
        jdbc.sql("""
                insert into consent_record (learner_id, keep_recordings, over_13, policy_version, recorded_at)
                values (?, ?, true, ?, ?)""")
                .params(c.learnerId().value(), c.keepRecordings(), c.policyVersion(), Timestamp.from(c.recordedAt()))
                .update();
    }

    @Override
    public Optional<Consent> latest(LearnerId learner) {
        return jdbc.sql("""
                select keep_recordings, policy_version, recorded_at from consent_record
                where learner_id = ? order by id desc limit 1""")
                .param(learner.value())
                .query((rs, n) -> new Consent(learner, rs.getBoolean("keep_recordings"),
                        rs.getString("policy_version"), rs.getTimestamp("recorded_at").toInstant()))
                .optional();
    }

    @Override
    public void create(byte[] tokenHash, LearnerId learner, Instant now) {
        jdbc.sql("insert into web_session (token_hash, learner_id, created_at, last_seen_at) values (?, ?, ?, ?)")
                .params(tokenHash, learner.value(), Timestamp.from(now), Timestamp.from(now))
                .update();
    }

    @Override
    public Optional<LearnerId> touch(byte[] tokenHash, Instant notBefore, Instant now) {
        return jdbc.sql("""
                update web_session set last_seen_at = ? where token_hash = ? and last_seen_at > ?
                returning learner_id""")
                .params(Timestamp.from(now), tokenHash, Timestamp.from(notBefore))
                .query((rs, n) -> new LearnerId(rs.getObject("learner_id", UUID.class)))
                .optional();
    }

    @Override
    public void deleteIdleSince(Instant cutoff) {
        jdbc.sql("delete from web_session where last_seen_at < ?").param(Timestamp.from(cutoff)).update();
    }

    @Override
    public void deleteGuestsWithoutConsentCreatedBefore(Instant cutoff) {
        jdbc.sql("""
                delete from learner l where l.kind = 'guest' and l.created_at < ?
                  and not exists (select 1 from consent_record c where c.learner_id = l.id)""")
                .param(Timestamp.from(cutoff)).update();
    }
}
