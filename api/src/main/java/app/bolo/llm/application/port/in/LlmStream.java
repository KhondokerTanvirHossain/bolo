package app.bolo.llm.application.port.in;

import java.util.concurrent.CompletableFuture;

/** A reply being generated. Token counts are live, so a cancelled speculative reply is still metered. */
public interface LlmStream {

    /** Stops generation; done() then completes exceptionally with CancellationException. */
    void cancel();

    /** The full reply, or exceptionally with LlmUnavailableException / CancellationException. */
    CompletableFuture<LlmResult> done();

    long inputTokens();

    long outputTokens();
}
