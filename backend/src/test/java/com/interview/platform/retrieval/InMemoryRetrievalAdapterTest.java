package com.interview.platform.retrieval;

import com.interview.platform.retrieval.persistence.RetrievalChunkMapper;
import com.interview.platform.llm.EmbedPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class InMemoryRetrievalAdapterTest {

    @Mock private EmbedPort embedPort;
    @Mock private RetrievalChunkMapper retrievalChunkMapper;
    @Mock private RetrievalSourcePort retrievalSourcePort;

    private InMemoryRetrievalAdapter retrieval;

    @BeforeEach
    void setUp() {
        retrieval = new InMemoryRetrievalAdapter(
            embedPort,
            retrievalChunkMapper,
            retrievalSourcePort,
            0.7,
            0.3
        );
    }

    @Test
    void hybridSearchUsesConfiguredKeywordWeight() {
        float[] vector = new float[]{1.0f, 0.0f};
        when(embedPort.embed(anyString())).thenReturn(vector);
        retrieval.index(
            RetrievalPort.SCOPE_SESSION,
            100L,
            List.of(
                "Experienced backend developer.",
                "Looking for a Spring Cloud expert with Redis experience."
            )
        );

        List<String> results = retrieval.search(
            RetrievalPort.SCOPE_SESSION,
            100L,
            "Redis Spring Cloud",
            5
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0)).contains("Redis").contains("Spring Cloud");
    }

    @Test
    void cacheMissRebuildsFromSourceAndPersistsChunks() {
        when(retrievalChunkMapper.selectList(any())).thenReturn(List.of());
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
        verify(retrievalChunkMapper, atLeastOnce()).insert(any(RetrievalChunk.class));
    }

    @Test
    void processRestartRebuildsFromPersistedChunksWithoutDomainRead() {
        RetrievalChunk persisted = new RetrievalChunk();
        persisted.setOrdinal(0);
        persisted.setContent("Persisted Redis Spring Cloud context");
        when(retrievalChunkMapper.selectList(any())).thenReturn(List.of(persisted));
        when(embedPort.embed(anyString())).thenReturn(new float[]{1.0f});

        List<String> results = retrieval.search(
            RetrievalPort.SCOPE_SESSION,
            300L,
            "Redis",
            1
        );

        assertThat(results).containsExactly("Persisted Redis Spring Cloud context");
        verifyNoInteractions(retrievalSourcePort);
    }

    @Test
    void embeddingFailureDegradesToEmptyResults() {
        when(embedPort.embed(anyString())).thenThrow(new IllegalStateException("embedding down"));
        retrieval.index(RetrievalPort.SCOPE_SESSION, 400L, List.of("context"));

        assertThat(retrieval.search(RetrievalPort.SCOPE_SESSION, 400L, "query", 5)).isEmpty();
    }
}
