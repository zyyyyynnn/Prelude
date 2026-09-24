package com.prelude.context;

import com.prelude.llm.api.EmbedPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hybrid retrieval invariants: index/search ranking, keyword fallback when
 * embedding fails, persistence rebuild, and invalidation.
 */
class InMemoryRetrievalAdapterTest {

    private static final String SCOPE_TYPE = RetrievalPort.SCOPE_SESSION;
    private static final Long SCOPE_ID = 9L;

    private final EmbedPort embedPort = mock(EmbedPort.class);
    private final RetrievalChunkStore chunkStore = mock(RetrievalChunkStore.class);
    private final RetrievalSourcePort sourcePort = mock(RetrievalSourcePort.class);

    private InMemoryRetrievalAdapter adapter;
    private final List<RetrievalChunkStore.StoredChunk> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        adapter = new InMemoryRetrievalAdapter(embedPort, chunkStore, sourcePort, 0.7, 0.3);
        when(embedPort.modelVersion()).thenReturn("test-embed-v1");
        when(embedPort.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            // Deterministic tiny embedding: first token hash drives dimension 0.
            float signal = text.toLowerCase().contains("spring") ? 1.0f : 0.0f;
            float other = text.toLowerCase().contains("react") ? 1.0f : 0.0f;
            return new float[] {signal, other, 0.1f};
        });
        when(chunkStore.load(eq(SCOPE_TYPE), eq(SCOPE_ID))).thenAnswer(invocation -> List.copyOf(stored));
        org.mockito.Mockito.doAnswer(invocation -> {
            stored.clear();
            stored.addAll(invocation.getArgument(2));
            return null;
        }).when(chunkStore).replace(eq(SCOPE_TYPE), eq(SCOPE_ID), org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.doAnswer(invocation -> {
            stored.clear();
            return null;
        }).when(chunkStore).delete(eq(SCOPE_TYPE), eq(SCOPE_ID));
    }

    @Test
    void indexThenSearchReturnsMatchingChunksInScoreOrder() {
        adapter.index(SCOPE_TYPE, SCOPE_ID, List.of(
            "Spring Boot powers the interview backend",
            "React builds the workspace UI",
            "Unrelated gardening notes"
        ));

        List<String> hits = adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring backend", 2);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).contains("Spring Boot");
        assertThat(stored).hasSize(3);
        assertThat(stored).allMatch(chunk -> chunk.embeddingModel().equals("test-embed-v1"));
    }

    @Test
    void blankOrNonPositiveTopKQueriesReturnEmptyWithoutTouchingTheIndex() {
        adapter.index(SCOPE_TYPE, SCOPE_ID, List.of("Spring interview backend"));

        assertThat(adapter.search(SCOPE_TYPE, SCOPE_ID, null, 3)).isEmpty();
        assertThat(adapter.search(SCOPE_TYPE, SCOPE_ID, "  ", 3)).isEmpty();
        assertThat(adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring", 0)).isEmpty();
    }

    @Test
    void embeddingFailureFallsBackToKeywordScoring() {
        when(embedPort.embed(anyString())).thenThrow(new IllegalStateException("embed unavailable"));

        adapter.index(SCOPE_TYPE, SCOPE_ID, List.of(
            "Spring Boot interview transcript",
            "Weekend hiking trail notes"
        ));

        List<String> hits = adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring interview", 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).contains("Spring Boot");
    }

    @Test
    void searchRebuildsFromPersistedChunksWhenMemoryIndexIsMissing() {
        stored.add(new RetrievalChunkStore.StoredChunk(
            0,
            "Persisted Spring interview context",
            "hash-1",
            "test-embed-v1",
            new float[] {1.0f, 0.0f, 0.1f}
        ));
        adapter = new InMemoryRetrievalAdapter(embedPort, chunkStore, sourcePort, 0.7, 0.3);

        List<String> hits = adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring interview", 3);

        assertThat(hits).containsExactly("Persisted Spring interview context");
        verify(sourcePort, never()).loadDocuments(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void searchRebuildsFromSourcePortWhenNothingIsPersisted() {
        when(sourcePort.loadDocuments(eq(SCOPE_TYPE), eq(SCOPE_ID)))
            .thenReturn(List.of("ResumeEntity mentions Spring Boot interviews"));

        List<String> hits = adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring Boot", 3);

        assertThat(hits).containsExactly("ResumeEntity mentions Spring Boot interviews");
        assertThat(stored).hasSize(1);
        verify(sourcePort).loadDocuments(SCOPE_TYPE, SCOPE_ID);
    }

    @Test
    void invalidateDropsMemoryAndPersistenceSoLaterSearchNeedsARebuild() {
        adapter.index(SCOPE_TYPE, SCOPE_ID, List.of("Spring Boot interview transcript"));
        adapter.invalidate(SCOPE_TYPE, SCOPE_ID);

        assertThat(stored).isEmpty();
        assertThat(adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring", 3)).isEmpty();
        verify(chunkStore).delete(SCOPE_TYPE, SCOPE_ID);
    }

    @Test
    void unknownScopeWithoutSourceDocumentsReturnsEmpty() {
        when(sourcePort.loadDocuments(eq(SCOPE_TYPE), eq(SCOPE_ID))).thenReturn(List.of());

        assertThat(adapter.search(SCOPE_TYPE, SCOPE_ID, "Spring", 3)).isEmpty();
    }
}
