package app.bolo.reminder.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.reminder.application.port.out.ReminderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcReminderAdapter implements ReminderPort {

    private final JdbcClient jdbc;

    @Override
    public Optional<Settings> find(LearnerId learner) {
        return jdbc.sql("select * from reminder_settings where learner_id = ?").param(learner.value())
                .query((rs, n) -> settings(rs)).optional();
    }

    static Settings settings(ResultSet rs) throws SQLException {
        return new Settings(rs.getBoolean("enabled"), rs.getString("local_time"), rs.getString("time_zone"), rs.getBoolean("keep_private"),
                instant(rs, "asked_at"), rs.getInt("ignored"), instant(rs, "stopped_at"), rs.getBoolean("comeback_sent"),
                date(rs, "last_sent_on"), instant(rs, "last_sent_at"), rs.getObject("last_sent_id", UUID.class), rs.getBoolean("tapped"),
                rs.getInt("card_days"), date(rs, "card_last_on"), rs.getBoolean("card_dismissed"), rs.getString("line"), rs.getString("line_source"));
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    @Override
    public void ensure(LearnerId learner, Instant now) {
        jdbc.sql("insert into reminder_settings (learner_id, updated_at) values (?, ?) on conflict (learner_id) do nothing")
                .params(learner.value(), Timestamp.from(now)).update();
    }

    @Override
    public void saveChoices(LearnerId learner, boolean enabled, String localTime, String timeZone, boolean keepPrivate, Instant askedAt, Instant now) {
        jdbc.sql("""
                insert into reminder_settings (learner_id, enabled, local_time, time_zone, keep_private, asked_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (learner_id) do update set enabled = excluded.enabled, local_time = excluded.local_time,
                    time_zone = excluded.time_zone, keep_private = excluded.keep_private,
                    asked_at = coalesce(reminder_settings.asked_at, excluded.asked_at), updated_at = excluded.updated_at""")
                .params(learner.value(), enabled, localTime, timeZone, keepPrivate, Timestamp.from(askedAt), Timestamp.from(now)).update();
    }

    @Override
    public void clearBackOff(LearnerId learner, String timeZone, Instant now) {
        jdbc.sql("""
                update reminder_settings set ignored = 0, stopped_at = null, comeback_sent = false,
                    time_zone = coalesce(?, time_zone), updated_at = ? where learner_id = ?""")
                .params(timeZone, Timestamp.from(now), learner.value()).update();
    }

    @Override
    public boolean markTapped(LearnerId learner, UUID sent) {
        return jdbc.sql("update reminder_settings set tapped = true where learner_id = ? and last_sent_id = ?")
                .params(learner.value(), sent).update() > 0;
    }

    @Override
    public void countCardDay(LearnerId learner, LocalDate day) {
        jdbc.sql("""
                update reminder_settings set card_days = card_days + 1, card_last_on = ?
                where learner_id = ? and card_last_on is distinct from ?""")
                .params(Date.valueOf(day), learner.value(), Date.valueOf(day)).update();
    }

    @Override
    public void dismissCard(LearnerId learner) {
        jdbc.sql("update reminder_settings set card_dismissed = true where learner_id = ?").param(learner.value()).update();
    }

    @Override
    public void upsertDevice(LearnerId learner, String endpoint, String p256dh, String auth, String userAgent, Instant now) {
        jdbc.sql("""
                insert into push_subscription (learner_id, endpoint, p256dh, auth, user_agent, created_at) values (?, ?, ?, ?, ?, ?)
                on conflict (endpoint) do update set learner_id = excluded.learner_id, p256dh = excluded.p256dh,
                    auth = excluded.auth, user_agent = excluded.user_agent""")
                .params(learner.value(), endpoint, p256dh, auth, userAgent, Timestamp.from(now)).update();
    }

    @Override
    public void deleteDevice(LearnerId learner, String endpoint) {
        jdbc.sql("delete from push_subscription where learner_id = ? and endpoint = ?").params(learner.value(), endpoint).update();
    }

    @Override
    public int deviceCount(LearnerId learner) {
        return jdbc.sql("select count(*) from push_subscription where learner_id = ?").param(learner.value()).query(Integer.class).single();
    }

    @Override
    public List<Device> devices(LearnerId learner) {
        return jdbc.sql("select endpoint, p256dh, auth from push_subscription where learner_id = ? order by id").param(learner.value())
                .query((rs, n) -> new Device(rs.getString(1), rs.getString(2), rs.getString(3))).list();
    }

    @Override
    public void move(LearnerId from, LearnerId to) {
        boolean has = jdbc.sql("select count(*) from reminder_settings where learner_id = ?").param(to.value()).query(Integer.class).single() > 0;
        if (has) jdbc.sql("delete from reminder_settings where learner_id = ?").param(from.value()).update();
        else jdbc.sql("update reminder_settings set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
        jdbc.sql("update push_subscription set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
    }

    @Override
    public List<Due> candidates() {
        return jdbc.sql("""
                select r.* from reminder_settings r
                where r.enabled and exists (select 1 from push_subscription p where p.learner_id = r.learner_id)""")
                .query((rs, n) -> new Due(new LearnerId(rs.getObject("learner_id", UUID.class)), settings(rs))).list();
    }

    @Override
    public boolean claim(LearnerId learner, LocalDate day, UUID id, Instant at, int ignored, boolean comeback) {
        return jdbc.sql("""
                update reminder_settings set last_sent_on = ?, last_sent_at = ?, last_sent_id = ?, tapped = false, ignored = ?,
                    comeback_sent = comeback_sent or ?
                where learner_id = ? and enabled and last_sent_on is distinct from ?""")
                .params(Date.valueOf(day), Timestamp.from(at), id, ignored, comeback, learner.value(), Date.valueOf(day)).update() > 0;
    }

    @Override
    public void stop(LearnerId learner, int ignored, Instant at) {
        jdbc.sql("update reminder_settings set stopped_at = coalesce(stopped_at, ?), ignored = ? where learner_id = ?")
                .params(Timestamp.from(at), ignored, learner.value()).update();
    }

    @Override
    public void saveLine(LearnerId learner, String line, String source, Instant now) {
        jdbc.sql("""
                insert into reminder_settings (learner_id, line, line_source, updated_at) values (?, ?, ?, ?)
                on conflict (learner_id) do update set line = excluded.line, line_source = excluded.line_source""")
                .params(learner.value(), line, source, Timestamp.from(now)).update();
    }

    @Override
    public void deleteEndpoint(String endpoint) {
        jdbc.sql("delete from push_subscription where endpoint = ?").param(endpoint).update();
    }

    @Override
    public void markOk(String endpoint, Instant at) {
        jdbc.sql("update push_subscription set last_ok_at = ? where endpoint = ?").params(Timestamp.from(at), endpoint).update();
    }
}
