package com.prelude.llm;

import java.util.function.Consumer;

public interface ChatPort {

    String complete(ChatRequest request);

    void stream(ChatRequest request, Consumer<String> onDelta);
}
