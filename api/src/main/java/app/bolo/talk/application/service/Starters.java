package app.bolo.talk.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** TLK-6 / TLK-5: two sentence starters the learner can read aloud to answer Bolo's last line. */
@Service
public class Starters {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final List<String> FALLBACK = List.of("I think that…", "For example, …");
    static final String SYSTEM = """
            Write 2 short sentence starters (3 to 6 words, ending with "…") that a nervous English learner could read \
            aloud to begin answering the question below. Simple everyday words.""";
    static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false,
            "required", List.of("starters"), "properties", Map.of("starters", Map.of("type", "array", "items", Map.of("type", "string"))));

    private final LlmClient llm;
    private final String model;

    public Starters(LlmClient llm, @Value("${bolo.llm.buddy-model}") String model) {
        this.llm = llm;
        this.model = model;
    }

    public List<String> forLine(TalkContext ctx, String buddyLine) {
        try {
            var request = new LlmRequest(model, SYSTEM, List.of(LlmMessage.user(buddyLine == null ? "Tell me about your day." : buddyLine)),
                    120, SCHEMA, true);
            JsonNode node = JSON.readTree(llm.stream(ctx, request, d -> { }).done().get(5, TimeUnit.SECONDS).text()).path("starters");
            List<String> out = new ArrayList<>();
            node.forEach(n -> { if (out.size() < 2 && !n.asString("").isBlank()) out.add(n.asString("").trim()); });
            return out.size() == 2 ? out : FALLBACK;
        } catch (Exception e) {
            return FALLBACK;
        }
    }
}
