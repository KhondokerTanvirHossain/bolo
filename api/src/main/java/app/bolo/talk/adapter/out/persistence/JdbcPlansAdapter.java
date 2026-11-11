package app.bolo.talk.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.talk.application.port.in.PlansUseCase.Plan;
import app.bolo.talk.application.port.out.PlansPort;
import app.bolo.talk.domain.PlanAllowance.Sub;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
class JdbcPlansAdapter implements PlansPort {

    private final JdbcClient jdbc;

    @Override
    public void insert(LearnerId learner, String plan, Instant startsAt, Instant endsAt, String source, String note, Instant at) {
        jdbc.sql("insert into subscription (learner_id, plan, starts_at, ends_at, source, note, created_at) values (?, ?, ?, ?, ?, ?, ?)")
                .params(learner.value(), plan, Timestamp.from(startsAt), Timestamp.from(endsAt), source, note, Timestamp.from(at)).update();
    }

    @Override
    public List<Sub> subscriptions(LearnerId learner) {
        return jdbc.sql("select plan, starts_at, ends_at from subscription where learner_id = ?").param(learner.value())
                .query((rs, n) -> new Sub(rs.getString("plan"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant()))
                .list();
    }

    @Override
    public List<Plan> plans() {
        return jdbc.sql("select plan, monthly_talks, price_bdt, price_usd from plan_setting order by monthly_talks")
                .query((rs, n) -> new Plan(rs.getString("plan"), rs.getInt("monthly_talks"), rs.getInt("price_bdt"), rs.getBigDecimal("price_usd")))
                .list();
    }

    @Override
    public void savePlan(Plan p) {
        jdbc.sql("update plan_setting set monthly_talks = ?, price_bdt = ?, price_usd = ? where plan = ?")
                .params(p.monthlyTalks(), p.priceBdt(), p.priceUsd(), p.plan()).update();
    }

    @Override
    public String setting(String key, String fallback) {
        return jdbc.sql("select value from app_setting where key = ?").param(key).query(String.class).optional().orElse(fallback);
    }

    @Override
    public void setSetting(String key, String value) {
        jdbc.sql("insert into app_setting (key, value) values (?, ?) on conflict (key) do update set value = excluded.value")
                .params(key, value).update();
    }

    @Override
    public int startsSince(Instant since) {
        return jdbc.sql("""
                select count(*) from talk_session s join learner l on l.id = s.learner_id
                where s.started_at >= ? and not l.unlimited""").param(Timestamp.from(since)).query(Integer.class).single();
    }
}
