package app.bolo.learner.adapter.out.persistence;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.out.SettingsPort;
import app.bolo.learner.domain.Settings;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JdbcSettingsAdapter implements SettingsPort {

    private final JdbcClient jdbc;

    @Override
    public void upsert(LearnerId learner, Settings s, Instant now) {
        jdbc.sql("""
                insert into learner_settings (learner_id, buddy_speed, captions, talk_view, voice_gender, voice_accent, mood,
                    answer_time, stammer, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (learner_id) do update set buddy_speed = excluded.buddy_speed, captions = excluded.captions,
                    talk_view = excluded.talk_view, voice_gender = excluded.voice_gender, voice_accent = excluded.voice_accent,
                    mood = excluded.mood, answer_time = excluded.answer_time, stammer = excluded.stammer,
                    updated_at = excluded.updated_at""")
                .params(learner.value(), s.buddySpeed(), s.captions(), s.talkView(), s.voiceGender(), s.voiceAccent(), s.mood(),
                        s.answerTime(), s.stammer(), Timestamp.from(now)).update();
    }

    @Override
    public Optional<Settings> find(LearnerId learner) {
        return jdbc.sql("""
                select buddy_speed, captions, talk_view, voice_gender, voice_accent, mood, answer_time, answer_time_auto, stammer
                from learner_settings where learner_id = ?""")
                .param(learner.value())
                .query((rs, n) -> new Settings(rs.getString("buddy_speed"), rs.getBoolean("captions"), rs.getString("talk_view"),
                        rs.getString("voice_gender"), rs.getString("voice_accent"), rs.getString("mood"), rs.getString("answer_time"),
                        rs.getString("answer_time_auto"), rs.getBoolean("stammer"))).optional();
    }

    @Override
    public void recordAutoAnswerTime(LearnerId learner, String speed, Instant now) {
        jdbc.sql("""
                insert into learner_settings (learner_id, answer_time_auto, updated_at) values (?, ?, ?)
                on conflict (learner_id) do update set answer_time_auto = excluded.answer_time_auto""")
                .params(learner.value(), speed, Timestamp.from(now)).update();
    }
}
