package app.bolo.talk.adapter.out.persistence;

import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.talk.application.port.out.TurnPort;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
class JdbcTurnAdapter implements TurnPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Override
    public void saveLearner(TalkSessionId session, int idx, String text, String audioKey, long streamStartMs, List<ScoredWord> words) {
        jdbc.sql("""
                insert into talk_turn (session_id, idx, speaker, text, audio_key, stream_start_ms, scored, at)
                values (?, ?, 'learner', ?, ?, ?, cast(? as jsonb), ?)
                on conflict (session_id, idx) do nothing""")
                .params(session.value(), idx, text, audioKey, streamStartMs, JSON.writeValueAsString(words), Timestamp.from(clock.now()))
                .update();
    }

    @Override
    public void updateScored(TalkSessionId session, int idx, List<ScoredWord> words) {
        jdbc.sql("update talk_turn set scored = cast(? as jsonb) where session_id = ? and idx = ? and speaker = 'learner'")
                .params(JSON.writeValueAsString(words), session.value(), idx)
                .update();
    }
}
