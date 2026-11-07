package app.bolo.llm.adapter.out.fake;

import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmResult;
import app.bolo.llm.application.port.in.LlmStream;
import app.bolo.llm.application.port.in.LlmUnavailableException;
import app.bolo.llm.application.port.out.LlmPort;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** For tests and keyless development: a fixed friendly reply in 4-character deltas, 30 ms apart. Can be told to fail. */
public class FakeLlm implements LlmPort {

    public static final String REPLY = "That sounds great. What do you do at work?";

    /** What the fake Coach answers to a structured (JSON-schema) request: valid against the transcript "hello bolo". */
    public static final String STRUCTURED_REPLY = """
            {"strength":{"turn":1,"quote":"hello bolo","text":"You greeted Bolo straight away, warmly."},
             "upgrades":[{"turn":1,"said":"hello bolo","better":"Hello Bolo, it's nice to talk with you."},
                         {"turn":3,"said":"hello bolo","better":"Hello again, Bolo, I have more to tell you."}],
             "sentenceLevel":"GOOD","vocabularyLevel":"BUILDING"}""";

    private volatile String reply = REPLY;
    private volatile String structuredReply = STRUCTURED_REPLY;
    private volatile boolean failStructured;
    private volatile RuntimeException failWith;
    private volatile long deltaMs = 30;

    @Override
    public String vendor() {
        return "fake";
    }

    public void reply(String reply) {
        this.reply = reply;
    }

    public void structuredReply(String json) {
        this.structuredReply = json == null ? STRUCTURED_REPLY : json;
    }

    public void failStructured(boolean fail) {
        this.failStructured = fail;
    }

    public void failWith(RuntimeException e) {
        this.failWith = e;
    }

    public void deltaMs(long ms) {
        this.deltaMs = ms;
    }

    @Override
    public LlmStream stream(LlmRequest request, Consumer<String> onDelta) {
        CompletableFuture<LlmResult> done = new CompletableFuture<>();
        boolean structured = request.jsonSchema() != null;
        String text = structured ? structuredReply : reply;
        RuntimeException fail = structured ? (failStructured ? new IllegalStateException("coach down") : null) : failWith;
        long pause = deltaMs;
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                if (fail != null) throw new LlmUnavailableException("fake failure", fail);
                for (int i = 0; i < text.length(); i += 4) {
                    if (done.isDone()) return;
                    Thread.sleep(pause);
                    if (done.isDone()) return;
                    onDelta.accept(text.substring(i, Math.min(text.length(), i + 4)));
                }
                done.complete(new LlmResult(text, request.system().length() / 4, text.length() / 4));
            } catch (InterruptedException e) {
                done.completeExceptionally(new CancellationException());
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        return new LlmStream() {
            @Override
            public void cancel() {
                done.completeExceptionally(new CancellationException());
                worker.interrupt();
            }

            @Override
            public CompletableFuture<LlmResult> done() {
                return done;
            }

            @Override
            public long inputTokens() {
                return request.system().length() / 4;
            }

            @Override
            public long outputTokens() {
                return text.length() / 4;
            }
        };
    }
}
