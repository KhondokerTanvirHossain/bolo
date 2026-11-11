package app.bolo.billing.adapter.out.persistence;

import app.bolo.billing.application.port.in.PaymentUseCase.Mine;
import app.bolo.billing.application.port.in.PaymentUseCase.Payment;
import app.bolo.billing.application.port.out.PaymentPort;
import app.bolo.common.id.LearnerId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcPaymentAdapter implements PaymentPort {

    private final JdbcClient jdbc;

    @Override
    public Optional<String> setting(String key) {
        return jdbc.sql("select value from app_setting where key = ?").param(key).query(String.class).optional();
    }

    @Override
    public void setSetting(String key, String value) {
        jdbc.sql("insert into app_setting (key, value) values (?, ?) on conflict (key) do update set value = excluded.value")
                .params(key, value).update();
    }

    @Override
    public boolean trxUsed(String trxId) {
        return jdbc.sql("select count(*) from payment where trx_id = ?").param(trxId).query(Integer.class).single() > 0;
    }

    @Override
    public boolean hasPending(LearnerId learner) {
        return jdbc.sql("select count(*) from payment where learner_id = ? and status = 'pending'").param(learner.value())
                .query(Integer.class).single() > 0;
    }

    @Override
    public int countSince(LearnerId learner, Instant since) {
        return jdbc.sql("select count(*) from payment where learner_id = ? and created_at >= ?")
                .params(learner.value(), Timestamp.from(since)).query(Integer.class).single();
    }

    @Override
    public void insert(LearnerId learner, String plan, int amountBdt, String trxId, String payerNumber, Instant at) {
        jdbc.sql("""
                insert into payment (learner_id, plan, amount_bdt, trx_id, payer_number, status, created_at)
                values (?, ?, ?, ?, ?, 'pending', ?)""")
                .params(learner.value(), plan, amountBdt, trxId, payerNumber, Timestamp.from(at)).update();
    }

    @Override
    public Optional<Mine> latest(LearnerId learner) {
        return jdbc.sql("select status, plan, trx_id, reason from payment where learner_id = ? order by created_at desc, id desc limit 1")
                .param(learner.value())
                .query((rs, n) -> new Mine(rs.getString("status"), rs.getString("plan"), rs.getString("trx_id"), rs.getString("reason")))
                .optional();
    }

    private static Payment row(ResultSet rs, int n) throws SQLException {
        return new Payment(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(), rs.getString("email"), rs.getString("plan"),
                rs.getInt("amount_bdt"), rs.getString("trx_id"), rs.getString("payer_number"), rs.getString("status"), rs.getString("reason"));
    }

    @Override
    public List<Payment> forAdmin() {
        String cols = "select p.id, p.created_at, a.email, p.plan, p.amount_bdt, p.trx_id, p.payer_number, p.status, p.reason "
                + "from payment p left join account a on a.learner_id = p.learner_id ";
        List<Payment> out = new ArrayList<>(jdbc.sql(cols + "where p.status = 'pending' order by p.created_at").query(JdbcPaymentAdapter::row).list());
        out.addAll(jdbc.sql(cols + "where p.status <> 'pending' order by p.decided_at desc limit 50").query(JdbcPaymentAdapter::row).list());
        return out;
    }

    @Override
    public Optional<Decided> decide(long id, String status, String reason, LearnerId admin, Instant at) {
        return jdbc.sql("""
                update payment set status = ?, reason = ?, decided_at = ?, decided_by = ?
                where id = ? and status = 'pending' returning learner_id, plan, trx_id""")
                .params(status, reason, Timestamp.from(at), admin.value(), id)
                .query((rs, n) -> new Decided(new LearnerId(rs.getObject("learner_id", UUID.class)), rs.getString("plan"), rs.getString("trx_id")))
                .optional();
    }
}
