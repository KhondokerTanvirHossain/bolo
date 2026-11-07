package app.bolo.llm.application.port.out;

import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmStream;

import java.util.function.Consumer;

public interface LlmPort {

    String vendor();

    LlmStream stream(LlmRequest request, Consumer<String> onDelta);
}
