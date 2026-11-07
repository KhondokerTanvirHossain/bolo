package app.bolo.talk.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** TLK-14: a cheap classifier on each committed learner turn — does it show risk of self-harm or a crisis? */
@Service
public class SafetyGuard {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String SYSTEM = """
            You check one thing a learner just said aloud in an English practice chat. Answer crisis=true only if the \
            words suggest risk of self-harm or suicide, or that the person is in danger or crisis and needs help now. \
            Everyday complaints, jokes ("this deadline is killing me") and sad but safe feelings are crisis=false.""";
    static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false,
            "required", List.of("crisis"), "properties", Map.of("crisis", Map.of("type", "boolean")));

    private final LlmClient llm;
    private final String model;

    public SafetyGuard(LlmClient llm, @Value("${bolo.llm.buddy-model}") String model) {
        this.llm = llm;
        this.model = model;
    }

    /**
     * The classifier's verdict. A failure completes exceptionally, so each caller decides: a keyword-matched turn
     * treats it as a crisis (fail closed); the parallel check on ordinary turns treats it as no crisis.
     */
    public CompletableFuture<Boolean> isCrisis(TalkContext ctx, String said) {
        var request = new LlmRequest(model, SYSTEM, List.of(LlmMessage.user(said)), 50, SCHEMA, true);
        return llm.stream(ctx, request, d -> { }).done().thenApply(r -> {
            var verdict = JSON.readTree(r.text()).path("crisis");
            if (!verdict.isBoolean()) throw new IllegalStateException("no verdict");
            return verdict.asBoolean();
        });
    }
}
