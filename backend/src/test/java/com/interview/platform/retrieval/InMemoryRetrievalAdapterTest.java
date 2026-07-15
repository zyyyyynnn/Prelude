package com.interview.platform.retrieval;

import com.interview.platform.llm.EmbedPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryRetrievalAdapterTest {

    @Mock private EmbedPort embedPort;
    @Mock private RetrievalChunkStore chunkStore;
    @Mock private RetrievalSourcePort retrievalSourcePort;

    private InMemoryRetrievalAdapter retrieval;

    @BeforeEach
    void setUp() {
        when(embedPort.modelVersion()).thenReturn("test-v1");
        retrieval = new InMemoryRetrievalAdapter(
            embedPort,
            chunkStore,
            retrievalSourcePort,
            0.2,
            0.8
        );
    }

    @Test
    void hybridSearchScoresEveryKeywordCandidateInsteadOfVectorPrefiltering() {
        when(embedPort.embed(anyString())).thenReturn(
            new float[]{1.0f, 0.0f},
            new float[]{0.9f, 0.1f},
            new float[]{-1.0f, 0.0f},
            new float[]{1.0f, 0.0f}
        );
        retrieval.index(
            RetrievalPort.SCOPE_SESSION,
            100L,
            List.of(
                "Experienced backend developer.",
                "Distributed systems engineer.",
                "Redis Spring Cloud specialist."
            )
        );

        List<String> results = retrieval.search(
            RetrievalPort.SCOPE_SESSION,
            100L,
            "Redis Spring Cloud",
            1
        );

        assertThat(results).containsExactly("Redis Spring Cloud specialist.");
    }

    @Test
    void cacheMissRebuildsFromSourceAndPersistsCompleteSnapshot() {
        when(chunkStore.load(RetrievalPort.SCOPE_SESSION, 200L)).thenReturn(List.of());
        when(retrievalSourcePort.loadDocuments(RetrievalPort.SCOPE_SESSION, 200L))
            .thenReturn(List.of("Resume Java", "JD Redis Spring Cloud"));
        when(embedPort.embed(anyString())).thenReturn(new float[]{1.0f});

        List<String> results = retrieval.search(
            RetrievalPort.SCOPE_SESSION,
            200L,
            "Redis Spring Cloud",
            5
        );

        assertThat(results).anyMatch(text -> text.contains("Redis"));
        verify(retrievalSourcePort).loadDocuments(RetrievalPort.SCOPE_SESSION, 200L);
        verify(chunkStore).replace(
            org.mockito.ArgumentMatchers.eq(RetrievalPort.SCOPE_SESSION),
            org.mockito.ArgumentMatchers.eq(200L),
            anyList()
        );
    }

    @Test
    void processRestartUsesCurrentPersistedVectorsWithoutDomainReadOrReembeddingChunks() {
        when(chunkStore.load(RetrievalPort.SCOPE_SESSION, 300L)).thenReturn(List.of(
            stored(0, "Persisted Redis Spring Cloud context", "test-v1", new float[]{1.0f})
        ));
        when(embedPort.embed("Redis")).thenReturn(new float[]{1.0f});

        List<String> results = retrieval.search(
            RetrievalPort.SCOPE_SESSION,
            300L,
            "Redis",
            1
        );

        assertThat(results).containsExactly("Persisted Redis Spring Cloud context");
        verify(embedPort).embed("Redis");
        verify(chunkStore, never()).replace(anyString(), org.mockito.ArgumentMatchers.anyLong(), anyList());
        verifyNoInteractions(retrievalSourcePort);
    }

    @Test
    void stalePersistedVectorIsRefreshedBeforeSearch() {
        when(chunkStore.load(RetrievalPort.SCOPE_SESSION, 350L)).thenReturn(List.of(
            stored(0, "Redis context", "old-v1", new float[]{0.0f})
        ));
        when(embedPort.embed(anyString())).thenReturn(new float[]{1.0f});

        assertThat(retrieval.search(RetrievalPort.SCOPE_SESSION, 350L, "Redis", 1))
            .containsExactly("Redis context");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RetrievalChunkStore.StoredChunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(chunkStore).replace(
            org.mockito.ArgumentMatchers.eq(RetrievalPort.SCOPE_SESSION),
            org.mockito.ArgumentMatchers.eq(350L),
            chunks.capture()
        );
        assertThat(chunks.getValue().get(0).embeddingModel()).isEqualTo("test-v1");
    }

    @Test
    void queryEmbeddingFailureFallsBackToKeywordSearch() {
        when(embedPort.embed(anyString()))
            .thenReturn(new float[]{1.0f})
            .thenThrow(new IllegalStateException("embedding down"));
        retrieval.index(RetrievalPort.SCOPE_SESSION, 400L, List.of("Redis context"));

        assertThat(retrieval.search(RetrievalPort.SCOPE_SESSION, 400L, "Redis", 5))
            .containsExactly("Redis context");
        verify(chunkStore, atLeastOnce()).replace(
            org.mockito.ArgumentMatchers.eq(RetrievalPort.SCOPE_SESSION),
            org.mockito.ArgumentMatchers.eq(400L),
            anyList()
        );
    }

    private RetrievalChunkStore.StoredChunk stored(
        int ordinal,
        String content,
        String model,
        float[] embedding
    ) {
        return new RetrievalChunkStore.StoredChunk(ordinal, content, "hash-" + ordinal, model, embedding);
    }
}
