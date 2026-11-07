package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.learner.application.port.in.SweepSessionsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Nothing grows forever on the shared disk: idle sessions and abandoned guests are swept. */
@SpringBootTest
class SessionSweepAcceptanceTest extends PostgresTestBase {

    @Autowired JdbcClient jdbc;
    @Autowired SweepSessionsUseCase sweep;

    UUID learner(String kind, Instant created) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into learner (id, kind, created_at) values (?, ?, ?)").params(id, kind, Timestamp.from(created)).update();
        return id;
    }

    void session(UUID learner, Instant lastSeen) {
        byte[] hash = new byte[32];
        new java.security.SecureRandom().nextBytes(hash);
        jdbc.sql("insert into web_session (token_hash, learner_id, created_at, last_seen_at) values (?, ?, ?, ?)")
                .params(hash, learner, Timestamp.from(lastSeen), Timestamp.from(lastSeen)).update();
    }

    void consent(UUID learner) {
        jdbc.sql("""
                insert into consent_record (learner_id, keep_recordings, over_13, policy_version, recorded_at)
                values (?, true, true, '2026-11-v1', now())""").param(learner).update();
    }

    int count(String sql, UUID id) {
        return jdbc.sql(sql).param(id).query(Integer.class).single();
    }

    @Test
    void sweepsIdleSessionsAndGuestsWhoNeverConsented() {
        Instant now = Instant.now();
        UUID abandoned = learner("guest", now.minus(Duration.ofDays(2)));
        session(abandoned, now.minus(Duration.ofDays(2)));
        UUID fresh = learner("guest", now.minus(Duration.ofHours(1)));
        UUID consented = learner("guest", now.minus(Duration.ofDays(10)));
        consent(consented);
        session(consented, now.minus(Duration.ofDays(91)));

        sweep.sweep();

        assertThat(count("select count(*) from learner where id = ?", abandoned)).isZero();
        assertThat(count("select count(*) from learner where id = ?", fresh)).isOne();
        assertThat(count("select count(*) from learner where id = ?", consented)).isOne();
        assertThat(count("select count(*) from web_session where learner_id = ?", consented)).isZero();
    }
}
