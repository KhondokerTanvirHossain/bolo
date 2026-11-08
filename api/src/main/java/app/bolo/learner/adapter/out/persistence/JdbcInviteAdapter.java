package app.bolo.learner.adapter.out.persistence;

import app.bolo.learner.application.port.out.InvitePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcInviteAdapter implements InvitePort {

    private final JdbcClient jdbc;

    @Override
    public boolean consume(String code) {
        if (code == null || code.isBlank() || code.length() > 64) return false;
        return jdbc.sql("update invite set uses = uses + 1 where code = ? and uses < max_uses").param(code).update() == 1;
    }
}
