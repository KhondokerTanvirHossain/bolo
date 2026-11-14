package app.bolo.reminder;

import app.bolo.PostgresTestBase;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.memory.application.port.in.MemoryChanged;
import app.bolo.memory.application.port.in.MemoryUseCase;
import app.bolo.reminder.application.service.ReminderLineService;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/** The next reminder's story line is written after Bolo's notes change, from the follow-up the next talk opens with. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReminderLineAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ReminderLineService lines;
    @Autowired FakeLlm llm;
    @MockitoSpyBean MemoryUseCase memory;
    TalkTestClient client;
    LearnerId learner;

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        String email = "l" + UUID.randomUUID().toString().substring(0, 8) + "@x.com";
        String me = client.send("POST", "/api/auth/google", client.consentedGuest(5), "{\"idToken\":\"fake:" + UUID.randomUUID() + ":" + email + "\"}").cookie();
        learner = new LearnerId(UUID.fromString(JsonPath.read(client.send("GET", "/api/me", me, "").body(), "$.learnerId")));
        llm.reminderReply(null);
    }

    Map<String, Object> row() {
        return jdbc.sql("select line, line_source from reminder_settings where learner_id = ?").param(learner.value()).query().singleRow();
    }

    @Test
    void memoryChangeWritesLine() {
        doReturn(Optional.of("the client demo on Friday")).when(memory).nextFollowUp(learner);
        lines.write(new MemoryChanged(learner, new TalkSessionId(UUID.randomUUID())));
        assertThat(row()).containsEntry("line", "Bolo wants to know how the demo went.").containsEntry("line_source", "the client demo on Friday");
        assertThat(llm.lastReminderRequest()).contains("the client demo on Friday");
    }

    @Test
    void invalidModelLineIsDropped() {
        doReturn(Optional.of("the client demo on Friday")).when(memory).nextFollowUp(learner);
        llm.reminderReply("{\"line\":\"Practise your English today 🙂\"}");
        lines.write(new MemoryChanged(learner, new TalkSessionId(UUID.randomUUID())));
        assertThat(row().get("line")).isNull();
    }

    @Test
    void noFollowUpClearsLine() {
        doReturn(Optional.of("the client demo on Friday")).when(memory).nextFollowUp(learner);
        lines.write(new MemoryChanged(learner, new TalkSessionId(UUID.randomUUID())));
        doReturn(Optional.empty()).when(memory).nextFollowUp(learner);
        lines.write(new MemoryChanged(learner, new TalkSessionId(UUID.randomUUID())));
        assertThat(row().get("line")).isNull();
        assertThat(row().get("line_source")).isNull();
    }

    @Autowired app.bolo.reminder.application.service.ReminderSendService sender;
    @Autowired app.bolo.reminder.application.port.out.PushPort push;

    String meCookie;

    void dueNowWithADevice() throws Exception {
        java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        String time = java.time.LocalTime.ofInstant(now, java.time.ZoneId.of("Asia/Dhaka")).toString();
        jdbc.sql("""
                insert into reminder_settings (learner_id, enabled, local_time, time_zone, asked_at, updated_at)
                values (?, true, ?, 'Asia/Dhaka', now(), now())
                on conflict (learner_id) do update set enabled = true, local_time = excluded.local_time""").params(learner.value(), time).update();
        jdbc.sql("update reminder_settings set enabled = false where learner_id <> ?").param(learner.value()).update();
        jdbc.sql("insert into push_subscription (learner_id, endpoint, p256dh, auth, created_at) values (?, ?, 'k', 'a', now())")
                .params(learner.value(), "https://push.example/" + UUID.randomUUID()).update();
        ((app.bolo.reminder.adapter.out.push.FakePushAdapter) push).clear();
    }

    String sentBody() {
        var sent = ((app.bolo.reminder.adapter.out.push.FakePushAdapter) push).sent();
        assertThat(sent).hasSize(1);
        return JsonPath.read(sent.getFirst().json(), "$.body");
    }

    @Test
    void freshStoryLineIsSentAndPreviewed() throws Exception {
        doReturn(Optional.of("the client demo on Friday")).when(memory).nextFollowUp(learner);
        lines.write(new MemoryChanged(learner, new TalkSessionId(UUID.randomUUID())));
        dueNowWithADevice();
        sender.sendDue(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
        assertThat(sentBody()).isEqualTo("Bolo wants to know how the demo went.");
    }

    @Test
    void lineFromAnOlderFollowUpIsNotSent() throws Exception {
        doReturn(Optional.of("the client demo on Friday")).when(memory).nextFollowUp(learner);
        lines.write(new MemoryChanged(learner, new TalkSessionId(UUID.randomUUID())));
        // The next talk opened with that thread, so it rests; Bolo now has a different follow-up.
        doReturn(Optional.of("the bank job CV")).when(memory).nextFollowUp(learner);
        dueNowWithADevice();
        sender.sendDue(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
        assertThat(sentBody()).isNotEqualTo("Bolo wants to know how the demo went.");
    }

    @MockitoSpyBean app.bolo.reminder.application.port.out.ReminderPort reminderPort;

    /** Review (reminders, I3): a pass works from fresh settings, so opening Bolo mid-pass is never undone by a stop. */
    @Test
    void openingBoloMidPassIsNeverUndoneByAStop() throws Exception {
        dueNowWithADevice();
        java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        // Yesterday's reminder went out untapped; the learner had 2 ignored, then opened Bolo (ignored back to 0).
        jdbc.sql("""
                update reminder_settings set last_sent_on = current_date - 1, last_sent_at = ?, last_sent_id = gen_random_uuid(),
                    tapped = false, delivered = true, ignored = 0 where learner_id = ?""")
                .params(java.sql.Timestamp.from(now.minus(1, java.time.temporal.ChronoUnit.DAYS)), learner.value()).update();
        // The pass's list was read before the open: it still says ignored = 2.
        org.mockito.Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var real = (java.util.List<app.bolo.reminder.application.port.out.ReminderPort.Due>) inv.callRealMethod();
            return real.stream().map(d -> {
                var s = d.settings();
                return new app.bolo.reminder.application.port.out.ReminderPort.Due(d.learner(), new app.bolo.reminder.application.port.out.ReminderPort.Settings(
                        s.enabled(), s.localTime(), s.timeZone(), s.keepPrivate(), s.askedAt(), 2, s.stoppedAt(), s.comebackSent(), s.lastSentOn(),
                        s.lastSentAt(), s.lastSentId(), s.tapped(), s.cardDays(), s.cardLastOn(), s.cardDismissed(), s.line(), s.lineSource(), s.delivered()));
            }).toList();
        }).when(reminderPort).candidates();
        sender.sendDue(now);
        assertThat(jdbc.sql("select stopped_at from reminder_settings where learner_id = ?").param(learner.value()).query().singleRow().get("stopped_at")).isNull();
        assertThat(sentBody()).isNotBlank();
    }
}
