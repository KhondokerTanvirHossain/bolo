package app.bolo.talk;

import app.bolo.PostgresTestBase;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The talk relay end to end over a real socket, with fake speech and a fake LLM; turn thresholds shrunk 10x. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class TalkRelayAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeSpeechToText stt;
    @Autowired FakeLlm llm;
    @Autowired app.bolo.media.application.port.in.AudioStoreUseCase audio;

    TalkTestClient client;
    String cookie;
    String id;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
        id = TalkTestClient.id(client.startTalk(cookie));
    }

    @AfterEach
    void reset() {
        llm.failWith(null);
    }

    TalkSocket openAndHearTheOpening() throws Exception {
        TalkSocket s = new TalkSocket(port, id, cookie);
        s.await(3000, "\"buddy_done\"", "\"turn\":0");
        return s;
    }

    /** Opening heard, then the learner's turn starts. */
    TalkSocket yourTurn() throws Exception {
        TalkSocket s = openAndHearTheOpening();
        s.json("{\"type\":\"played\",\"turn\":0}");
        s.await(2000, "\"state\"", "\"your_turn\"");
        return s;
    }

    @Test
    void buddySpeaksFirstWithCaptionAndAudio() throws Exception {
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(2000, "\"state\"", "\"buddy_speaking\"");
            s.await(3000, "\"caption\"", "\"turn\":0");
            assertThat(s.awaitAudio(2000)).isNotEmpty();
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
        }
    }

    @Test
    void aTurnIsCommittedAfterSilenceAndAnswered() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(2000, "\"turn_end\"", "\"turn\":1");
            s.await(3000, "\"caption\"", "\"turn\":2");
            assertThat(s.awaitAudio(2000)).isNotEmpty();
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
        }
        var rows = jdbc.sql("select idx, speaker, text, server_ms from talk_turn where session_id = ? order by idx")
                .param(UUID.fromString(id)).query().listOfRows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("speaker", "buddy");
        assertThat(rows.get(1)).containsEntry("speaker", "learner").containsEntry("text", "hello bolo");
        assertThat(rows.get(2)).containsEntry("speaker", "buddy");
        assertThat(rows.get(2).get("server_ms")).isNotNull();
    }

    @Test
    void doneEndsTheTurnAtOnce() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.json("{\"type\":\"done\"}");
            s.await(200, "\"turn_end\"", "\"turn\":1");
        }
    }

    @Test
    void framesWhileBuddySpeaksAreNotHeard() throws Exception {
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(3000, "\"caption\"", "\"turn\":0");
            s.tone(1000);
            s.await(3000, "\"buddy_done\"", "\"turn\":0");
            assertThat(stt.streams().getLast().loudFrames()).isZero();
            assertThat(s.quiet(1000, "\"turn_end\"")).isTrue();
        }
    }

    @Test
    void silenceOnlyNeverEndsTheTurn() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.zeros(2000);
            assertThat(s.quiet(500, "\"turn_end\"")).isTrue();
        }
    }

    @Test
    void endClosesTheSessionAndFreesTheSlot() throws Exception {
        try (TalkSocket s = openAndHearTheOpening()) {
            s.json("{\"type\":\"end\"}");
            assertThat(s.await(2000, "\"ended\"", "\"learner_ended\"")).contains("\"turns\":0");
        }
        assertThat(endReason()).isEqualTo("learner_ended");
        assertThat(client.startTalk(client.consentedGuest(5)).status()).isEqualTo(201);
    }

    @Test
    void droppedSocketFreesTheSlot() throws Exception {
        TalkSocket s = yourTurn();
        s.tone(400);
        s.zeros(600);
        s.await(2000, "\"turn_end\"", "\"turn\":1");
        s.abort();
        long until = System.currentTimeMillis() + 2000;
        while (endReason() == null && System.currentTimeMillis() < until) Thread.sleep(50);
        assertThat(endReason()).isEqualTo("disconnected");
        assertThat(client.startTalk(client.consentedGuest(5)).status()).isEqualTo(201);
    }

    @Test
    void vendorFailureEndsKindly() throws Exception {
        llm.failWith(new IllegalStateException("down"));
        try (TalkSocket s = new TalkSocket(port, id, cookie)) {
            s.await(3000, "\"ended\"", "\"voice_unavailable\"");
        }
        long until = System.currentTimeMillis() + 2000;
        while (endReason() == null && System.currentTimeMillis() < until) Thread.sleep(50);
        assertThat(endReason()).isEqualTo("voice_unavailable");
    }

    @Test
    void heardIsStored() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"heard\",\"turn\":2,\"ms\":840}");
            s.json("{\"type\":\"end\"}");
            assertThat(s.await(2000, "\"ended\"")).contains("\"p50Ms\":840").contains("\"turns\":1");
        }
        Map<String, Object> row = jdbc.sql("select heard_ms from talk_turn where session_id = ? and idx = 2")
                .param(UUID.fromString(id)).query().singleRow();
        assertThat(row.get("heard_ms")).isEqualTo(840);
    }

    /** Review #3: a real browser sends heard as Bolo's voice starts, before buddy_done; it must still count. */
    @Test
    void heardBeforeBuddyDoneCounts() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(2000, "\"turn_end\"", "\"turn\":1");
            s.awaitAudio(3000);
            s.json("{\"type\":\"heard\",\"turn\":2,\"ms\":700}");
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
            s.json("{\"type\":\"end\"}");
            assertThat(s.await(2000, "\"ended\"")).contains("\"p50Ms\":700");
        }
        assertThat(jdbc.sql("select heard_ms from talk_turn where session_id = ? and idx = 2")
                .param(UUID.fromString(id)).query(Integer.class).single()).isEqualTo(700);
    }

    /** Review #5: audio pushed far faster than real time doesn't reach the (billed) recogniser. */
    @Test
    void audioFasterThanRealTimeIsDropped() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.tone(10_000);
            Thread.sleep(500);
            assertThat(stt.streams().getLast().loudFrames()).isLessThan(250);
        }
    }

    /** Coach v1: each learner turn keeps its audio (encrypted) and its pronunciation scores. */
    @Test
    void aLearnerTurnKeepsItsAudioAndScores() throws Exception {
        try (TalkSocket s = yourTurn()) {
            s.tone(400);
            s.zeros(600);
            s.await(3000, "\"buddy_done\"", "\"turn\":2");
        }
        Map<String, Object> row = jdbc.sql("select audio_key, stream_start_ms, scored::text as scored from talk_turn where session_id = ? and idx = 1")
                .param(UUID.fromString(id)).query().singleRow();
        assertThat((String) row.get("audio_key")).isEqualTo("talk/" + id + "/turn-1.wav");
        assertThat(row.get("stream_start_ms")).isNotNull();
        assertThat((String) row.get("scored")).contains("\"hello\"").contains("\"bolo\"");
        byte[] wav = audio.get("talk/" + id + "/turn-1.wav").orElseThrow();
        assertThat(new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(wav.length).as("400 ms of speech + silence up to the commit").isGreaterThan(44 + 32 * 600);
    }

    String endReason() {
        return jdbc.sql("select end_reason from talk_session where id = ?").param(UUID.fromString(id))
                .query(String.class).optional().orElse(null);
    }
}
