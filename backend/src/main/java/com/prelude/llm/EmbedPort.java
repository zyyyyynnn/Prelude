package com.prelude.llm;

public interface EmbedPort {

    float[] embed(String text);

    String modelVersion();
}
