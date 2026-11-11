package app.bolo.help.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.help.application.port.in.HelpUseCase.Request;
import app.bolo.help.application.port.out.HelpPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
class JdbcHelpAdapter implements HelpPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcClient jdbc;

    @Override
    public void insert(LearnerId learner, String message, String contextJson, Instant at) {
        jdbc.sql("insert into help_request (learner_id, message, context, created_at) values (?, ?, cast(? as jsonb), ?)")
                .params(learner.value(), message, contextJson, Timestamp.from(at)).update();
    }

    @Override
    public int countSince(LearnerId learner, Instant since) {
        return jdbc.sql("select count(*) from help_request where learner_id = ? and created_at >= ?")
                .params(learner.value(), Timestamp.from(since)).query(Integer.class).single();
    }

    @Override
    public List<Request> open() {
        return jdbc.sql("""
                select h.id, a.email, h.message, h.context::text as context, h.created_at
                from help_request h left join account a on a.learner_id = h.learner_id
                where h.done_at is null order by h.created_at desc limit 200""")
                .query((rs, n) -> new Request(rs.getLong("id"), rs.getString("email"), rs.getString("message"),
                        JSON.readValue(rs.getString("context"), new TypeReference<Map<String, String>>() { }),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public void moveLearner(LearnerId from, LearnerId to) {
        jdbc.sql("update help_request set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
    }

    @Override
    public void done(long id, Instant at) {
        jdbc.sql("update help_request set done_at = ? where id = ? and done_at is null").params(Timestamp.from(at), id).update();
    }
}
