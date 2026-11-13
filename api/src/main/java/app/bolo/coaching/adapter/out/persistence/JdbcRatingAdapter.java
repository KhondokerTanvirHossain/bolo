package app.bolo.coaching.adapter.out.persistence;

import app.bolo.coaching.application.port.out.RatingPort;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
class JdbcRatingAdapter implements RatingPort {

    private final JdbcClient jdbc;

    @Override
    public boolean exists(TalkSessionId talk) {
        return jdbc.sql("select count(*) from talk_rating where talk_id = ?").param(talk.value()).query(Integer.class).single() > 0;
    }

    @Override
    public void upsert(TalkSessionId talk, LearnerId learner, Integer score, String note, Instant at) {
        jdbc.sql("""
                insert into talk_rating (talk_id, learner_id, score, note, created_at, updated_at) values (?, ?, ?, ?, ?, ?)
                on conflict (talk_id) do update set score = coalesce(excluded.score, talk_rating.score),
                    note = case when excluded.score is null then talk_rating.note else excluded.note end,
                    updated_at = excluded.updated_at""")
                .params(talk.value(), learner.value(), score, note, Timestamp.from(at), Timestamp.from(at)).update();
    }

    @Override
    public void move(LearnerId from, LearnerId to) {
        jdbc.sql("update talk_rating set learner_id = ? where learner_id = ?").params(to.value(), from.value()).update();
    }
}
