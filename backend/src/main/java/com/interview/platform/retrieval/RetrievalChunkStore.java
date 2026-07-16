package com.interview.platform.retrieval;

import java.util.List;

public interface RetrievalChunkStore {

    List<StoredChunk> load(String scopeType, Long scopeId);

    void replace(String scopeType, Long scopeId, List<StoredChunk> chunks);

    void delete(String scopeType, Long scopeId);

    record StoredChunk(
        int ordinal,
        String content,
        String contentHash,
        String embeddingModel,
        float[] embedding
    ) {
    }
}
