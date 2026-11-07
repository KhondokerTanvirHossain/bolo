package app.bolo.learner.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.out.ProfilePort;
import app.bolo.learner.domain.FearedMoment;
import app.bolo.learner.domain.Goal;
import app.bolo.learner.domain.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JdbcProfileAdapter implements ProfilePort {

    private final JdbcClient jdbc;

    @Override
    public void upsert(LearnerId learner, Profile p, Instant now) {
        jdbc.sql("""
                insert into learner_profile (learner_id, goal, feared_moment, talk_minutes, updated_at)
                values (?, ?, ?, ?, ?)
                on conflict (learner_id) do update set goal = excluded.goal, feared_moment = excluded.feared_moment,
                    talk_minutes = excluded.talk_minutes, updated_at = excluded.updated_at""")
                .params(learner.value(), p.goal().key(), p.fearedMoment().key(), p.talkMinutes(), Timestamp.from(now))
                .update();
    }

    @Override
    public Optional<Profile> find(LearnerId learner) {
        return jdbc.sql("select goal, feared_moment, talk_minutes from learner_profile where learner_id = ?")
                .param(learner.value())
                .query((rs, n) -> new Profile(Goal.fromKey(rs.getString("goal")).orElseThrow(),
                        FearedMoment.fromKey(rs.getString("feared_moment")).orElseThrow(), rs.getInt("talk_minutes")))
                .optional();
    }
}
