package app.bolo.talk.adapter.out.persistence;

import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.common.id.LearnerId;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.talk.application.port.in.TalkRecord;
import app.bolo.talk.application.port.out.TurnPort;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public void saveLearner(TalkSessionId session, int idx, String text, String audioKey, long streamStartMs, List<ScoredWord> words,
                            Integer startMs) {
        jdbc.sql("""
                insert into talk_turn (session_id, idx, speaker, text, audio_key, stream_start_ms, scored, at, start_ms)
                values (?, ?, 'learner', ?, ?, ?, cast(? as jsonb), ?, ?)
                on conflict (session_id, idx) do nothing""")
                .params(session.value(), idx, text, audioKey, streamStartMs, JSON.writeValueAsString(words), Timestamp.from(clock.now()),
                        startMs)
                .update();
    }

    @Override
    public void updateScored(TalkSessionId session, int idx, List<ScoredWord> words) {
        jdbc.sql("update talk_turn set scored = cast(? as jsonb) where session_id = ? and idx = ? and speaker = 'learner'")
                .params(JSON.writeValueAsString(words), session.value(), idx)
                .update();
    }

    @Override
    public Optional<TalkRecord> record(TalkSessionId session) {
        var head = jdbc.sql("select learner_id, minutes from talk_session where id = ?").param(session.value())
                .query((rs, n) -> new Object[]{new LearnerId(rs.getObject("learner_id", UUID.class)), rs.getInt("minutes")})
                .optional();
        if (head.isEmpty()) return Optional.empty();
        List<TalkRecord.Turn> turns = jdbc.sql("""
                select idx, speaker, text, audio_key, stream_start_ms, scored::text as scored, coalesce(heard_ms, server_ms) as latency
                from talk_turn where session_id = ? order by idx""")
                .param(session.value())
                .query((rs, n) -> new TalkRecord.Turn(rs.getInt("idx"), rs.getString("speaker"), rs.getString("text"),
                        rs.getString("audio_key"), (Long) rs.getObject("stream_start_ms"), words(rs.getString("scored")),
                        (Integer) rs.getObject("latency")))
                .list();
        return Optional.of(new TalkRecord((LearnerId) head.get()[0], (Integer) head.get()[1], turns));
    }

    private static List<ScoredWord> words(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(JSON.readValue(json, ScoredWord[].class));
    }

    @Override
    public List<Integer> recentStartDelays(app.bolo.common.id.LearnerId learner, int limit) {
        return jdbc.sql("""
                select t.start_ms from talk_turn t join talk_session s on s.id = t.session_id
                where s.learner_id = ? and t.speaker = 'learner' and t.start_ms is not null
                order by t.at desc, t.id desc limit ?""")
                .params(learner.value(), limit).query(Integer.class).list();
    }
}
