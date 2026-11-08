package app.bolo.learner.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.out.AccountPort;
import app.bolo.learner.application.port.out.LearnerMergePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcAccountAdapter implements AccountPort, LearnerMergePort {

    private final JdbcClient jdbc;

    @Override
    public Optional<LearnerId> learnerFor(String firebaseUid) {
        return jdbc.sql("select learner_id from account where firebase_uid = ?").param(firebaseUid)
                .query((rs, n) -> new LearnerId(rs.getObject("learner_id", UUID.class))).optional();
    }

    @Override
    public void insert(LearnerId learner, String firebaseUid, String email, String name, Instant at) {
        jdbc.sql("insert into account (learner_id, firebase_uid, email, name, created_at, last_sign_in_at) values (?, ?, ?, ?, ?, ?)")
                .params(learner.value(), firebaseUid, email, name, Timestamp.from(at), Timestamp.from(at)).update();
    }

    @Override
    public void signedIn(LearnerId learner, String email, String name, Instant at) {
        jdbc.sql("update account set email = ?, name = ?, last_sign_in_at = ? where learner_id = ?")
                .params(email, name, Timestamp.from(at), learner.value()).update();
    }

    @Override
    public Optional<Account> find(LearnerId learner) {
        return jdbc.sql("select email, name from account where learner_id = ?").param(learner.value())
                .query((rs, n) -> new Account(rs.getString("email"), rs.getString("name"))).optional();
    }

    @Override
    public void moveIfAccountHasNone(LearnerId from, LearnerId to) {
        for (String table : new String[]{"consent_record", "learner_profile", "learner_settings"}) {
            jdbc.sql("update " + table + " set learner_id = ? where learner_id = ? and not exists (select 1 from " + table
                    + " where learner_id = ?)").params(to.value(), from.value(), to.value()).update();
        }
    }
}
