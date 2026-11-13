package app.bolo.memory;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.memory.application.port.out.MemoryPort;
import app.bolo.memory.application.service.MemoryService;
import app.bolo.memory.domain.Line;
import app.bolo.memory.domain.MemoryNote;
import app.bolo.memory.domain.MemoryThread;
import app.bolo.talk.TalkTestClient;
import app.bolo.talk.application.port.in.TalkEnded;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** P3b (spec 2026-11-13 "Bolo knows you"): after a talk, Bolo updates its short note about the learner. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.speech.vendor=fake", "bolo.llm.vendor=fake"})
class MemoryUpdateAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeLlm llm;
    @Autowired MemoryService memory;
    @Autowired MemoryPort port_;
    @Autowired ApplicationEventPublisher events;
    @Autowired BoloClock clock;

    TalkTestClient client;
    TalkSessionId talk;
    LearnerId learner;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
        talk = newTalk(client.consentedGuest(5));
        learner = new LearnerId(jdbc.sql("select learner_id from talk_session where id = ?").param(talk.value()).query(UUID.class).single());
        llm.deltaMs(1);
    }

    @AfterEach
    void reset() {
        llm.memoryReply(null);
        llm.failMemory(false);
        llm.deltaMs(30);
    }

    TalkSessionId newTalk(String cookie) throws Exception {
        TalkSessionId id = new TalkSessionId(UUID.fromString(TalkTestClient.id(client.startTalk(cookie))));
        String[][] turns = {{"buddy", "Hi! What are you working on?"}, {"learner", "I am sending my CV to a bank job"},
                {"buddy", "Nice. When?"}, {"learner", "On Thursday I will send it"}};
        for (int i = 0; i < turns.length; i++) {
            jdbc.sql("insert into talk_turn (session_id, idx, speaker, text, at) values (?, ?, ?, ?, now())")
                    .params(id.value(), i, turns[i][0], turns[i][1]).update();
        }
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where id = ?").param(id.value()).update();
        return id;
    }

    MemoryNote note() {
        return port_.find(learner).map(MemoryPort.Row::note).orElse(MemoryNote.empty());
    }

    void seed(MemoryNote n) {
        port_.save(learner, true, n, 0, null);
    }

    @Test
    void aTalkWithTwoTurnsWritesTheNote() {
        memory.update(talk, learner);
        MemoryNote n = note();
        assertThat(n.threads()).extracting(MemoryThread::text).containsExactly("Sending the CV to the bank job");
        assertThat(n.threads().getFirst().said()).isEqualTo(clock.today());
        assertThat(n.threads().getFirst().id()).isNotBlank();
        assertThat(n.recent()).first().isEqualTo("The bank job CV");
        assertThat(n.about()).extracting(Line::text).contains("Works as a software tester in Dhaka");
    }

    @Test
    void theEventUpdatesTheNoteOffThePath() throws Exception {
        events.publishEvent(new TalkEnded(talk, learner, 2, "learner_ended"));
        long until = System.currentTimeMillis() + 5000;
        while (note().threads().isEmpty() && System.currentTimeMillis() < until) Thread.sleep(50);
        assertThat(note().threads()).isNotEmpty();
    }

    @Test
    void aCrisisTalkIsNeverUsed() throws Exception {
        events.publishEvent(new TalkEnded(talk, learner, 2, "crisis"));
        Thread.sleep(800);
        assertThat(port_.find(learner).map(r -> r.note().isEmpty()).orElse(true)).isTrue();
    }

    @Test
    void oneTurnIsNotEnough() throws Exception {
        events.publishEvent(new TalkEnded(talk, learner, 1, "learner_ended"));
        Thread.sleep(800);
        assertThat(note().isEmpty()).isTrue();
    }

    @Test
    void rememberingOffWritesNothing() {
        port_.save(learner, false, MemoryNote.empty(), 0, null);
        memory.update(talk, learner);
        assertThat(note().isEmpty()).isTrue();
    }

    @Test
    void junkFromTheModelKeepsTheOldNote() {
        MemoryNote old = new MemoryNote(List.of(new Line("a1", "Lives in Mirpur")), List.of(), List.of(), List.of());
        seed(old);
        llm.memoryReply("not json at all");
        memory.update(talk, learner);
        assertThat(note()).isEqualTo(old);
        llm.memoryReply("{\"about\":\"nope\"}");
        memory.update(talk, learner);
        assertThat(note()).isEqualTo(old);
        llm.failMemory(true);
        memory.update(talk, learner);
        assertThat(note()).isEqualTo(old);
    }

    @Test
    void oversizedNoteIsCapped() {
        String threads = String.join(",", IntStream.range(0, 15).mapToObj(i ->
                "{\"id\":\"\",\"kind\":\"worry\",\"text\":\"worry " + i + " " + "z".repeat(200) + "\",\"said\":\"2026-11-13\",\"due\":\"\"}").toList());
        llm.memoryReply("{\"about\":[],\"threads\":[" + threads + "],\"interests\":[],\"topic\":\"Worries\"}");
        memory.update(talk, learner);
        assertThat(note().threads()).hasSize(8).allSatisfy(t -> assertThat(t.text().length()).isLessThanOrEqualTo(120));
    }

    @Test
    void keptLinesKeepTheirIdsAndOpenedMarks() {
        MemoryThread t = new MemoryThread("k1", MemoryThread.Kind.WORRY, "Nervous about Monday", LocalDate.of(2026, 11, 10), null,
                java.time.Instant.parse("2026-11-12T00:00:00Z"), 3);
        seed(new MemoryNote(List.of(), List.of(t), List.of(), List.of()));
        llm.memoryReply("{\"about\":[],\"threads\":[{\"id\":\"k1\",\"kind\":\"worry\",\"text\":\"Still nervous about Monday\",\"said\":\"2026-11-10\",\"due\":\"\"},"
                + "{\"id\":\"zz-made-up\",\"kind\":\"prep\",\"text\":\"Practising answers\",\"said\":\"2026-11-13\",\"due\":\"\"}],\"interests\":[],\"topic\":\"Monday\"}");
        memory.update(talk, learner);
        MemoryThread kept = note().threads().stream().filter(x -> x.id().equals("k1")).findFirst().orElseThrow();
        assertThat(kept.text()).isEqualTo("Still nervous about Monday");
        assertThat(kept.openedTalk()).isEqualTo(3);
        assertThat(note().threads()).extracting(MemoryThread::id).doesNotContain("zz-made-up").hasSize(2);
    }

    @Test
    void aLineRemovedDuringAnUpdateStaysRemoved() throws Exception {
        seed(new MemoryNote(List.of(new Line("gone", "Lives in Mirpur")), List.of(), List.of(), List.of()));
        llm.memoryReply("{\"about\":[{\"id\":\"gone\",\"text\":\"Lives in Mirpur\"}],\"threads\":[],\"interests\":[],\"topic\":\"Home\"}");
        llm.deltaMs(60);
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> memory.update(talk, learner));
        Thread.sleep(250);
        memory.remove(learner, "gone");
        running.get();
        assertThat(note().ids()).doesNotContain("gone");
    }

    @Test
    void turningOffDuringAnUpdateWritesNothing() throws Exception {
        llm.deltaMs(60);
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> memory.update(talk, learner));
        Thread.sleep(250);
        memory.setEnabled(learner, false);
        running.get();
        assertThat(note().isEmpty()).isTrue();
        assertThat(port_.find(learner).orElseThrow().enabled()).isFalse();
    }

    @Test
    void updatesForOneLearnerRunOneAtATime() throws Exception {
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> memory.update(talk, learner));
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> memory.update(talk, learner));
        CompletableFuture.allOf(a, b).get();
        assertThat(note().recent()).hasSize(2);
    }

    @Test
    void theUpdaterIsToldTheRules() {
        memory.update(talk, learner);
        assertThat(llm.lastStructuredRequest()).contains("health").contains("religion")
                .contains("Only what the learner said about themselves").contains("I am sending my CV to a bank job");
    }
}
