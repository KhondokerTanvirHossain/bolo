package app.bolo.admin.adapter.out.persistence;

import app.bolo.admin.application.port.out.AdminReportsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reports for the admin portal. Cost is an estimate from the usage ledger: speech $1 per audio hour, voice $16
 * per million characters, Claude $3 / $15 per million tokens in / out.
 */
@Repository
@RequiredArgsConstructor
class JdbcAdminReports implements AdminReportsPort {

    private final JdbcClient jdbc;

    @Override
    public List<User> users(String query, Instant monthStart) {
        String q = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        return jdbc.sql("""
                select l.id, a.email, a.name, l.kind, l.unlimited,
                       (select s.plan from subscription s join plan_setting p on p.plan = s.plan
                        where s.learner_id = l.id and s.starts_at <= now() and s.ends_at > now() order by p.monthly_talks desc limit 1) as plan,
                       (select max(s.ends_at) from subscription s where s.learner_id = l.id and s.starts_at <= now() and s.ends_at > now()) as plan_ends,
                       (select count(*) from talk_session t where t.learner_id = l.id and t.attached_at is not null and t.started_at >= ?) as talks_month,
                       (select max(t.started_at) from talk_session t where t.learner_id = l.id) as last_talk
                from learner l join account a on a.learner_id = l.id
                where lower(a.email) like ? or lower(coalesce(a.name, '')) like ?
                order by a.last_sign_in_at desc limit 50""")
                .params(Timestamp.from(monthStart), q, q)
                .query((rs, n) -> new User(rs.getObject("id").toString(), rs.getString("email"), rs.getString("name"), rs.getString("kind"),
                        rs.getBoolean("unlimited"), rs.getString("plan") == null ? "free" : rs.getString("plan"),
                        rs.getTimestamp("plan_ends") == null ? null : rs.getTimestamp("plan_ends").toInstant(), rs.getInt("talks_month"),
                        rs.getTimestamp("last_talk") == null ? null : rs.getTimestamp("last_talk").toInstant()))
                .list();
    }

    @Override
    public List<Day> days(LocalDate from, LocalDate to) {
        Map<LocalDate, int[]> talks = new HashMap<>();
        jdbc.sql("""
                select (started_at at time zone 'Asia/Dhaka')::date as d, count(*) as n,
                       coalesce(sum(round(extract(epoch from (coalesce(ended_at, now()) - attached_at)) / 60)), 0) as m
                from talk_session where attached_at is not null and (started_at at time zone 'Asia/Dhaka')::date between ? and ? group by 1""")
                .params(Date.valueOf(from), Date.valueOf(to))
                .query((rs, n) -> talks.put(rs.getDate("d").toLocalDate(), new int[]{rs.getInt("n"), rs.getInt("m")})).list();
        Map<LocalDate, Integer> accounts = new HashMap<>();
        jdbc.sql("select (created_at at time zone 'Asia/Dhaka')::date as d, count(*) as n from account where (created_at at time zone 'Asia/Dhaka')::date between ? and ? group by 1")
                .params(Date.valueOf(from), Date.valueOf(to))
                .query((rs, n) -> accounts.put(rs.getDate("d").toLocalDate(), rs.getInt("n"))).list();
        Map<LocalDate, BigDecimal> cost = new HashMap<>();
        jdbc.sql("""
                select (at at time zone 'Asia/Dhaka')::date as d, sum(case unit_type
                         when 'audio_ms' then units / 3600000.0 * 1.0
                         when 'chars' then units * 16 / 1000000.0
                         when 'tokens_in' then units * 3 / 1000000.0
                         when 'tokens_out' then units * 15 / 1000000.0 else 0 end) as usd
                from usage where (at at time zone 'Asia/Dhaka')::date between ? and ? group by 1""")
                .params(Date.valueOf(from), Date.valueOf(to))
                .query((rs, n) -> cost.put(rs.getDate("d").toLocalDate(), rs.getBigDecimal("usd"))).list();
        List<Day> out = new java.util.ArrayList<>();
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            int[] t = talks.getOrDefault(d, new int[]{0, 0});
            BigDecimal usd = cost.getOrDefault(d, BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
            out.add(new Day(d, t[0], t[1], accounts.getOrDefault(d, 0), usd));
        }
        return out;
    }

    @Override
    public int liveTalks(Instant since) {
        return jdbc.sql("select count(*) from talk_session where ended_at is null and attached_at is not null and started_at > ?")
                .param(Timestamp.from(since)).query(Integer.class).single();
    }
}
