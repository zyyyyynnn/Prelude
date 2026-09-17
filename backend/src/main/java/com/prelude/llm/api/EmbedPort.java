package com.prelude.llm.api;

public interface EmbedPort {

    float[] embed(String text);

    String modelVersion();
}
