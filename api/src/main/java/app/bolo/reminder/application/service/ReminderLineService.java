package app.bolo.reminder.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.common.time.BoloClock;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.memory.application.port.in.MemoryChanged;
import app.bolo.memory.application.port.in.MemoryUseCase;
import app.bolo.reminder.application.port.out.ReminderPort;
import app.bolo.reminder.domain.ReminderLines;
import app.bolo.reminder.domain.ReminderPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** After Bolo's notes change, writes the next reminder's story line from the follow-up the next talk would open with. */
@Slf4j
@Service
public class ReminderLineService {

    private final MemoryUseCase memory;
    private final ReminderPort reminders;
    private final LlmClient llm;
    private final BoloClock clock;
    private final JsonMapper json;
    private final String model;
    private final int maxTokens;

    public ReminderLineService(MemoryUseCase memory, ReminderPort reminders, LlmClient llm, BoloClock clock, JsonMapper json,
                               @Value("${bolo.reminder.model}") String model, @Value("${bolo.reminder.max-tokens}") int maxTokens) {
        this.memory = memory;
        this.reminders = reminders;
        this.llm = llm;
        this.clock = clock;
        this.json = json;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @EventListener
    public void onMemoryChanged(MemoryChanged e) {
        Thread.ofVirtual().name("reminder-line-" + e.learner().value()).start(() -> write(e));
    }

    /** Never throws: on any failure the line is cleared, so a plain line goes out instead. */
    public void write(MemoryChanged e) {
        try {
            String follow = memory.nextFollowUp(e.learner()).orElse(null);
            if (follow == null) {
                if (reminders.find(e.learner()).isPresent()) reminders.saveLine(e.learner(), null, null, clock.now());
                return;
            }
            var request = new LlmRequest(model, ReminderPrompt.system(), List.of(LlmMessage.user(ReminderPrompt.input(follow))), maxTokens,
                    ReminderPrompt.schema(), true);
            String text = llm.stream(new TalkContext(e.learner(), e.talk()), request, d -> { }).done().get(20, TimeUnit.SECONDS).text();
            String line = json.readTree(text).path("line").asString("").trim();
            if (ReminderLines.valid(line)) reminders.saveLine(e.learner(), line, follow, clock.now());
            else reminders.saveLine(e.learner(), null, null, clock.now());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("reminder line for {} failed: {}", e.learner().value(), ex.getClass().getSimpleName());
            try {
                reminders.saveLine(e.learner(), null, null, clock.now());
            } catch (RuntimeException ignored) {
                // the stale line is never sent anyway (freshness check)
            }
        }
    }
}
