package app.bolo.memory;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.memory.application.port.in.MemoryUseCase;
import app.bolo.memory.application.port.in.TalkBrief;
import app.bolo.memory.application.port.out.MemoryPort;
import app.bolo.memory.domain.Line;
import app.bolo.memory.domain.MemoryNote;
import app.bolo.memory.domain.MemoryThread;
import app.bolo.talk.TalkSocket;
import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P3b (TLK-11 changed 2026-11-13): each talk opens from what Bolo remembers, with one personal follow-up. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bolo.talk.max-live-sessions=1", "bolo.speech.vendor=fake", "bolo.llm.vendor=fake",
        "bolo.talk.speculate-after-ms=100", "bolo.talk.end-after-ms=300", "bolo.talk.unfinished-end-after-ms=500"})
class MemoryBriefAcceptanceTest extends PostgresTestBase {

    static final String PLAN = "Showing the payment bug demo to the client";

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired FakeLlm llm;
    @Autowired MemoryPort memory;
    @Autowired MemoryUseCase briefs;

    TalkTestClient client;
    String cookie;

    @BeforeEach
    void setUp() throws Exception {
        endAll();
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
    }

    void endAll() {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
    }

    String startTalk() throws Exception {
        endAll();
        return TalkTestClient.id(client.startTalk(cookie));
    }

    LearnerId learnerOf(String talk) {
        return new LearnerId(jdbc.sql("select learner_id from talk_session where id = ?").param(UUID.fromString(talk)).query(UUID.class).single());
    }

    /** Opens the talk's socket, waits for Bolo's opening line, and returns what Bolo was asked. */
    String opening(String talk) throws Exception {
        try (TalkSocket s = new TalkSocket(port, talk, cookie)) {
            s.await(3000, "\"caption\"", "\"turn\":0");
        }
        return llm.lastReplyRequest();
    }

    void seedPlan(LearnerId learner) {
        MemoryThread plan = new MemoryThread("p1", MemoryThread.Kind.PLAN, PLAN, LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12), null, null);
        memory.save(learner, true, new MemoryNote(List.of(new Line("a1", "Works as a software tester in Dhaka")), List.of(plan),
                List.of(new Line("i1", "Cricket")), List.of("The payment bug")), 0, null);
    }

    @Test
    void aTalkOpensWithTheFollowUp() throws Exception {
        String talk = startTalk();
        seedPlan(learnerOf(talk));
        String asked = opening(talk);
        assertThat(asked).contains("one follow-up about: " + PLAN).contains("Works as a software tester in Dhaka")
                .contains("Cricket").contains("The payment bug").doesNotContain("a real question about their situation");
    }

    @Test
    void theNextTwoTalksDontReuseIt() throws Exception {
        String first = startTalk();
        seedPlan(learnerOf(first));
        assertThat(opening(first)).contains("one follow-up about: " + PLAN);
        assertThat(opening(startTalk())).doesNotContain("one follow-up about").contains(PLAN);
        assertThat(opening(startTalk())).doesNotContain("one follow-up about");
    }

    @Test
    void aSecondBriefForTheSameTalkKeepsItsOpener() throws Exception {
        String talk = startTalk();
        LearnerId learner = learnerOf(talk);
        seedPlan(learner);
        TalkSessionId id = new TalkSessionId(UUID.fromString(talk));
        TalkBrief a = briefs.brief(learner, id, "work_meetings");
        TalkBrief b = briefs.brief(learner, id, "work_meetings");
        assertThat(a.followUp()).isTrue();
        assertThat(b.text()).contains("one follow-up about: " + PLAN);
        assertThat(memory.find(learner).orElseThrow().talks()).isEqualTo(1);
    }

    @Test
    void rememberingOffSendsNoNotes() throws Exception {
        String talk = startTalk();
        LearnerId learner = learnerOf(talk);
        memory.save(learner, false, MemoryNote.empty(), 0, null);
        String asked = opening(talk);
        assertThat(asked).doesNotContain("What you remember").contains("Ideas if the talk needs a new direction")
                .contains("a real question about their situation");
        assertThat(memory.find(learner).orElseThrow().talks()).isZero();
    }

    @Test
    void aFirstTalkOpensFromTheGoal() throws Exception {
        String asked = opening(startTalk());
        assertThat(asked).doesNotContain("What you remember").contains("a real question about their situation");
    }
}
