package app.bolo.llm.adapter.out.anthropic;

import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmResult;
import app.bolo.llm.application.port.in.LlmStream;
import app.bolo.llm.application.port.in.LlmUnavailableException;
import app.bolo.llm.application.port.out.LlmPort;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Claude over the streaming Messages API. Bolo's replies are short and latency-bound (NFR-1), so thinking is off at
 * effort low, which the Haiku model accepts. No sampling parameters: current models reject non-default values.
 */
@Slf4j
public class AnthropicLlm implements LlmPort {

    private final AnthropicClient client;

    public AnthropicLlm(String apiKey) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(20)).maxRetries(1).build();
    }

    @Override
    public String vendor() {
        return "anthropic";
    }

    @Override
    public LlmStream stream(LlmRequest request, Consumer<String> onDelta) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(request.maxTokens())
                .system(request.system())
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        for (LlmMessage m : request.messages()) {
            if (m.role() == LlmMessage.Role.USER) params.addUserMessage(m.text());
            else params.addAssistantMessage(m.text());
        }
        CompletableFuture<LlmResult> done = new CompletableFuture<>();
        AtomicLong in = new AtomicLong();
        AtomicLong out = new AtomicLong();
        AtomicReference<StreamResponse<RawMessageStreamEvent>> open = new AtomicReference<>();
        StringBuilder text = new StringBuilder();
        Thread.ofVirtual().start(() -> {
            try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params.build())) {
                open.set(response);
                if (done.isDone()) return;
                response.stream().forEach(event -> {
                    if (done.isDone()) throw new CancellationException();
                    event.messageStart().ifPresent(s -> in.set(s.message().usage().inputTokens()));
                    event.messageDelta().ifPresent(d -> out.set(d.usage().outputTokens()));
                    event.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> {
                        text.append(t.text());
                        onDelta.accept(t.text());
                    });
                });
                done.complete(new LlmResult(text.toString(), in.get(), out.get()));
            } catch (CancellationException e) {
                done.completeExceptionally(e);
            } catch (Exception e) {
                if (!done.isDone()) log.warn("anthropic stream failed: {}", e.getClass().getSimpleName());
                done.completeExceptionally(new LlmUnavailableException("anthropic stream failed", e));
            }
        });
        return new LlmStream() {
            @Override
            public void cancel() {
                done.completeExceptionally(new CancellationException());
                StreamResponse<RawMessageStreamEvent> r = open.get();
                if (r != null) {
                    try {
                        r.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public CompletableFuture<LlmResult> done() {
                return done;
            }

            @Override
            public long inputTokens() {
                return in.get();
            }

            @Override
            public long outputTokens() {
                return out.get();
            }
        };
    }
}
