package app.bolo.memory.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.memory.application.port.out.MemoryPort;
import app.bolo.memory.domain.MemoryNote;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
class JdbcMemoryAdapter implements MemoryPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcClient jdbc;

    @Override
    public Optional<Row> find(LearnerId learner) {
        return jdbc.sql("select enabled, note::text as note, talks, last_talk_id, epoch from learner_memory where learner_id = ?")
                .param(learner.value()).query(JdbcMemoryAdapter::row).optional();
    }

    @Override
    public Row lockOrCreate(LearnerId learner) {
        jdbc.sql("insert into learner_memory (learner_id) values (?) on conflict (learner_id) do nothing").param(learner.value()).update();
        return jdbc.sql("select enabled, note::text as note, talks, last_talk_id, epoch from learner_memory where learner_id = ? for update")
                .param(learner.value()).query(JdbcMemoryAdapter::row).single();
    }

    @Override
    public void save(LearnerId learner, boolean enabled, MemoryNote note, int talks, UUID lastTalkId) {
        jdbc.sql("""
                insert into learner_memory (learner_id, enabled, note, talks, last_talk_id, updated_at)
                values (?, ?, cast(? as jsonb), ?, ?, now())
                on conflict (learner_id) do update set enabled = excluded.enabled, note = excluded.note,
                    talks = excluded.talks, last_talk_id = excluded.last_talk_id, updated_at = now()""")
                .params(learner.value(), enabled, JSON.writeValueAsString(note), talks, lastTalkId).update();
    }

    @Override
    public void reset(LearnerId learner, boolean enabled) {
        jdbc.sql("update learner_memory set note = '{}', enabled = ?, epoch = epoch + 1, updated_at = now() where learner_id = ?")
                .params(enabled, learner.value()).update();
    }

    @Override
    public void move(LearnerId from, LearnerId to) {
        Optional<Row> guest = find(from);
        if (guest.isEmpty()) return;
        Optional<Row> account = find(to);
        if (account.isEmpty()) {
            jdbc.sql("update learner_memory set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
            return;
        }
        MemoryNote a = account.get().note();
        MemoryNote g = guest.get().note();
        LocalDate today = LocalDate.now(BoloClock.ZONE);
        MemoryNote merged = new MemoryNote(concat(a.about(), g.about()), concat(a.threads(), g.threads()),
                concat(a.interests(), g.interests()), concat(a.recent(), g.recent())).capped(today);
        Row kept = account.get();
        save(to, kept.enabled(), kept.enabled() ? merged : MemoryNote.empty(), kept.talks(), kept.lastTalkId());
        jdbc.sql("delete from learner_memory where learner_id = ?").param(from.value()).update();
    }

    private static <T> java.util.List<T> concat(java.util.List<T> a, java.util.List<T> b) {
        return Stream.concat(a.stream(), b.stream()).distinct().toList();
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        String json = rs.getString("note");
        MemoryNote note = json == null || json.equals("{}") ? MemoryNote.empty() : JSON.readValue(json, MemoryNote.class);
        return new Row(rs.getBoolean("enabled"), note, rs.getInt("talks"), rs.getObject("last_talk_id", UUID.class), rs.getInt("epoch"));
    }
}
