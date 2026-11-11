package app.bolo.billing.adapter.out.persistence;

import app.bolo.billing.application.port.in.WaitlistUseCase.Count;
import app.bolo.billing.application.port.in.WaitlistUseCase.Entry;
import app.bolo.billing.application.port.in.WaitlistUseCase.Mine;
import app.bolo.billing.application.port.out.WaitlistPort;
import app.bolo.common.id.LearnerId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JdbcWaitlistAdapter implements WaitlistPort {

    private final JdbcClient jdbc;

    @Override
    public void upsert(LearnerId learner, String plan, String region, String timeZone, Instant at) {
        jdbc.sql("""
                insert into waitlist (learner_id, plan, region, time_zone, created_at, updated_at) values (?, ?, ?, ?, ?, ?)
                on conflict (learner_id) do update set plan = excluded.plan, region = excluded.region,
                    time_zone = excluded.time_zone, updated_at = excluded.updated_at""")
                .params(learner.value(), plan, region, timeZone, Timestamp.from(at), Timestamp.from(at)).update();
    }

    @Override
    public Optional<Mine> find(LearnerId learner) {
        return jdbc.sql("select plan, region from waitlist where learner_id = ?").param(learner.value())
                .query((rs, n) -> new Mine(rs.getString("plan"), rs.getString("region"))).optional();
    }

    @Override
    public List<Entry> entries() {
        return jdbc.sql("""
                select a.email, w.plan, w.region, w.time_zone, w.updated_at
                from waitlist w left join account a on a.learner_id = w.learner_id
                order by w.updated_at desc limit 500""")
                .query((rs, n) -> new Entry(rs.getString("email"), rs.getString("plan"), rs.getString("region"),
                        rs.getString("time_zone"), rs.getTimestamp("updated_at").toInstant()))
                .list();
    }

    @Override
    public List<Count> counts() {
        return jdbc.sql("""
                select plan, count(*) filter (where region = 'bd') as bd, count(*) filter (where region = 'intl') as intl
                from waitlist group by plan order by plan""")
                .query((rs, n) -> new Count(rs.getString("plan"), rs.getInt("bd"), rs.getInt("intl"))).list();
    }

    @Override
    public void moveLearner(LearnerId from, LearnerId to) {
        jdbc.sql("""
                insert into waitlist (learner_id, plan, region, time_zone, created_at, updated_at)
                select ?, plan, region, time_zone, created_at, updated_at from waitlist where learner_id = ?
                on conflict (learner_id) do nothing""").params(to.value(), from.value()).update();
        jdbc.sql("delete from waitlist where learner_id = ?").param(from.value()).update();
    }
}
