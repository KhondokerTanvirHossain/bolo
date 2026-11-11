package app.bolo.help.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.error.TooManyRequestsException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.help.application.port.in.HelpUseCase;
import app.bolo.help.application.port.out.HelpPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HelpService implements HelpUseCase {

    static final int MAX_MESSAGE = 1000;
    static final int PER_HOUR = 5;
    /** Only these context keys are kept, each at most 200 characters: no transcripts, nothing else. */
    static final Set<String> CONTEXT_KEYS = Set.of("view", "talkId", "browser");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HelpPort port;
    private final BoloClock clock;

    @Override
    public void report(LearnerId learner, String message, Map<String, String> context) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty() || text.length() > MAX_MESSAGE) throw new RefusedException("message");
        var now = clock.now();
        if (port.countSince(learner, now.minus(Duration.ofHours(1))) >= PER_HOUR) throw new TooManyRequestsException();
        ObjectNode ctx = JSON.createObjectNode();
        if (context != null) {
            context.forEach((k, v) -> {
                if (CONTEXT_KEYS.contains(k) && v != null) ctx.put(k, v.length() > 200 ? v.substring(0, 200) : v);
            });
        }
        port.insert(learner, text, JSON.writeValueAsString(ctx), now);
    }

    @Override
    public List<Request> open() {
        return port.open();
    }

    /** Review (P2b): a guest who signs into an existing account keeps its reports (the guest row is deleted after). */
    @org.springframework.context.event.EventListener
    public void onLearnerMerged(app.bolo.common.event.LearnerMerged e) {
        port.moveLearner(e.from(), e.to());
    }

    @Override
    public void done(long id) {
        port.done(id, clock.now());
    }
}
