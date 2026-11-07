package app.bolo.llm;

import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmResult;
import app.bolo.llm.application.port.in.LlmStream;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeLlmClientTest {

    static final LlmRequest REQ = new LlmRequest("fake", "system", List.of(), 160);

    @Test
    void streamsTheReplyInSmallDeltas() throws Exception {
        List<String> deltas = new CopyOnWriteArrayList<>();
        LlmResult result = new FakeLlm().stream(REQ, deltas::add).done().get(5, TimeUnit.SECONDS);
        assertThat(result.text()).isEqualTo("That sounds great. What do you do at work?");
        assertThat(String.join("", deltas)).isEqualTo(result.text());
        assertThat(deltas).allMatch(d -> d.length() <= 4);
    }

    @Test
    void cancelStopsTheStream() throws Exception {
        List<String> deltas = new CopyOnWriteArrayList<>();
        LlmStream stream = new FakeLlm().stream(REQ, deltas::add);
        Thread.sleep(50);
        stream.cancel();
        int seen = deltas.size();
        Thread.sleep(200);
        assertThat(deltas).hasSize(seen);
        assertThatThrownBy(() -> stream.done().get(1, TimeUnit.SECONDS))
                .isInstanceOfAny(CancellationException.class, ExecutionException.class);
    }
}
