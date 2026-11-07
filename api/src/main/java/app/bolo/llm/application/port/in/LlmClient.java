package app.bolo.llm.application.port.in;

import app.bolo.common.id.TalkContext;

import java.util.function.Consumer;

public interface LlmClient {

    /** Streams text deltas to {@code onDelta} on a background thread; usage is metered to {@code ctx}. */
    LlmStream stream(TalkContext ctx, LlmRequest request, Consumer<String> onDelta);
}
