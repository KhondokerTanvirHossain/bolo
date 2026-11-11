package app.bolo.billing.adapter.out.persistence;

import app.bolo.billing.application.port.out.ReferralPort;
import app.bolo.common.id.LearnerId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcReferralAdapter implements ReferralPort {

    private final JdbcClient jdbc;

    private static Instant at(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    @Override
    public Optional<String> code(LearnerId learner) {
        return jdbc.sql("select code from referral_code where learner_id = ?").param(learner.value()).query(String.class).optional();
    }

    @Override
    public boolean insertCode(LearnerId learner, String code, Instant at) {
        // on conflict covers both a taken code and a code made for this learner by a request at the same moment.
        jdbc.sql("insert into referral_code (learner_id, code, created_at) values (?, ?, ?) on conflict do nothing")
                .params(learner.value(), code, Timestamp.from(at)).update();
        return code(learner).isPresent();
    }

    @Override
    public Optional<LearnerId> learnerForCode(String code) {
        return jdbc.sql("select learner_id from referral_code where code = ?").param(code)
                .query((rs, n) -> new LearnerId(rs.getObject(1, UUID.class))).optional();
    }

    @Override
    public void insertReferral(LearnerId friend, LearnerId referrer, Instant at) {
        jdbc.sql("insert into referral (friend_id, referrer_id, joined_at) values (?, ?, ?) on conflict do nothing")
                .params(friend.value(), referrer.value(), Timestamp.from(at)).update();
    }

    @Override
    public List<LearnerId> friends(LearnerId referrer) {
        return jdbc.sql("select friend_id from referral where referrer_id = ? order by joined_at").param(referrer.value())
                .query((rs, n) -> new LearnerId(rs.getObject(1, UUID.class))).list();
    }

    @Override
    public List<Reward> rewards(LearnerId learner) {
        return jdbc.sql("select n, ends_at, seen_at from referral_reward where learner_id = ? order by n").param(learner.value())
                .query((rs, n) -> new Reward(rs.getInt("n"), at(rs.getTimestamp("ends_at")), at(rs.getTimestamp("seen_at")))).list();
    }

    @Override
    public boolean insertReward(LearnerId learner, int n, Instant at) {
        return jdbc.sql("insert into referral_reward (learner_id, n, granted_at) values (?, ?, ?) on conflict do nothing")
                .params(learner.value(), n, Timestamp.from(at)).update() == 1;
    }

    @Override
    public void setRewardEnd(LearnerId learner, int n, Instant endsAt) {
        jdbc.sql("update referral_reward set ends_at = ? where learner_id = ? and n = ?")
                .params(Timestamp.from(endsAt), learner.value(), n).update();
    }

    @Override
    public void seen(LearnerId learner, Instant at) {
        jdbc.sql("update referral_reward set seen_at = ? where learner_id = ? and seen_at is null")
                .params(Timestamp.from(at), learner.value()).update();
    }
}
