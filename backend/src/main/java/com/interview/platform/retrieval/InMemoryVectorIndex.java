package com.interview.platform.retrieval;

import java.util.ArrayList;
import java.util.List;

final class InMemoryVectorIndex {

    record Entry(
        String text,
        String contentHash,
        String embeddingModel,
        float[] vector
    ) {
    }

    private final List<Entry> entries = new ArrayList<>();

    void add(Entry entry) {
        entries.add(entry);
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }
}
