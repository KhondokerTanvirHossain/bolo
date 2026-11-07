package app.bolo.talk.adapter.out.persistence;

import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.talk.application.port.out.TurnPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
class JdbcTurnAdapter implements TurnPort {

    private final JdbcClient jdbc;
    private final BoloClock clock;

    @Override
    public void save(TalkSessionId session, int idx, String speaker, String text, Integer serverMs, Integer heardMs) {
        jdbc.sql("""
                insert into talk_turn (session_id, idx, speaker, text, server_ms, heard_ms, at) values (?, ?, ?, ?, ?, ?, ?)
                on conflict (session_id, idx) do nothing""")
                .params(session.value(), idx, speaker, text, serverMs, heardMs, Timestamp.from(clock.now()))
                .update();
    }

    @Override
    public void heard(TalkSessionId session, int idx, int ms) {
        jdbc.sql("update talk_turn set heard_ms = ? where session_id = ? and idx = ? and speaker = 'buddy'")
                .params(ms, session.value(), idx)
                .update();
    }
}
