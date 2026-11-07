package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.talk.application.port.in.AudioRetentionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** NFR-14: with "No" to keeping recordings, talk audio is gone within 24 h; otherwise it's kept for 90 days. */
@SpringBootTest(properties = {"bolo.speech.vendor=fake", "bolo.llm.vendor=fake"})
class AudioRetentionAcceptanceTest extends PostgresTestBase {

    @Autowired JdbcClient jdbc;
    @Autowired AudioStoreUseCase audio;
    @Autowired AudioRetentionUseCase retention;

    UUID endedTalk(boolean keepRecordings, Instant endedAt) {
        UUID learner = UUID.randomUUID();
        UUID talk = UUID.randomUUID();
        jdbc.sql("insert into learner (id, kind, created_at) values (?, 'guest', now())").param(learner).update();
        jdbc.sql("insert into consent_record (learner_id, keep_recordings, over_13, policy_version, recorded_at) values (?, ?, true, '2026-11-v1', now())")
                .params(learner, keepRecordings).update();
        jdbc.sql("insert into talk_session (id, learner_id, minutes, started_at, ended_at, end_reason) values (?, ?, 5, ?, ?, 'learner_ended')")
                .params(talk, learner, Timestamp.from(endedAt.minusSeconds(300)), Timestamp.from(endedAt)).update();
        audio.put("talk/" + talk + "/turn-1.wav", new byte[]{1, 2, 3});
        return talk;
    }

    @Test
    void noConsentAudioGoesAfter24hAndKeptAudioAfter90Days() {
        Instant now = Instant.now();
        UUID noRecent = endedTalk(false, now.minus(2, ChronoUnit.HOURS));
        UUID noOld = endedTalk(false, now.minus(25, ChronoUnit.HOURS));
        UUID yesOld = endedTalk(true, now.minus(25, ChronoUnit.HOURS));
        UUID yesAncient = endedTalk(true, now.minus(91, ChronoUnit.DAYS));

        retention.sweep();

        assertThat(audio.get("talk/" + noRecent + "/turn-1.wav")).isPresent();
        assertThat(audio.get("talk/" + noOld + "/turn-1.wav")).isEmpty();
        assertThat(audio.get("talk/" + yesOld + "/turn-1.wav")).isPresent();
        assertThat(audio.get("talk/" + yesAncient + "/turn-1.wav")).isEmpty();
    }
}
