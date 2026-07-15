package com.interview.platform.retrieval.persistence;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.platform.retrieval.RetrievalChunkStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisRetrievalChunkStoreTest {

    @Mock private RetrievalChunkMapper mapper;

    @Test
    void replacePersistsModelDimensionsAndVectorSnapshot() {
        MybatisRetrievalChunkStore store = new MybatisRetrievalChunkStore(mapper, new ObjectMapper());

        store.replace("session", 7L, List.of(
            new RetrievalChunkStore.StoredChunk(0, "content", "hash", "model-v1", new float[]{1.0f, 2.0f})
        ));

        ArgumentCaptor<RetrievalChunk> row = ArgumentCaptor.forClass(RetrievalChunk.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getEmbeddingModel()).isEqualTo("model-v1");
        assertThat(row.getValue().getEmbeddingDimensions()).isEqualTo(2);
        assertThat(row.getValue().getEmbeddingJson()).isEqualTo("[1.0,2.0]");
    }

    @Test
    void invalidPersistedVectorDegradesToKeywordOnlyChunk() {
        RetrievalChunk row = new RetrievalChunk();
        row.setOrdinal(0);
        row.setContent("content");
        row.setContentHash("hash");
        row.setEmbeddingModel("model-v1");
        row.setEmbeddingDimensions(2);
        row.setEmbeddingJson("[1.0]");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        MybatisRetrievalChunkStore store = new MybatisRetrievalChunkStore(mapper, new ObjectMapper());

        assertThat(store.load("session", 7L)).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("content");
            assertThat(chunk.embedding()).isNull();
        });
    }
}
