package app.bolo.llm.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.common.usage.UsageRecorder;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmStream;
import app.bolo.llm.application.port.out.LlmPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** Every reply, finished or cancelled, lands in the usage ledger (NFR-22) - speculation waste included. */
@Service
@RequiredArgsConstructor
public class MeteredLlmClient implements LlmClient {

    private final LlmPort llm;
    private final UsageRecorder usage;

    @Override
    public LlmStream stream(TalkContext ctx, LlmRequest request, Consumer<String> onDelta) {
        LlmStream stream = llm.stream(request, onDelta);
        stream.done().whenComplete((r, e) -> {
            usage.record(ctx.learnerId(), ctx.sessionId(), llm.vendor(), "llm", stream.inputTokens(), "tokens_in");
            usage.record(ctx.learnerId(), ctx.sessionId(), llm.vendor(), "llm", stream.outputTokens(), "tokens_out");
        });
        return stream;
    }
}
