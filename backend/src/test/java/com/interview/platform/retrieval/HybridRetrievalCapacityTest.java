package com.interview.platform.retrieval;

import com.interview.platform.llm.EmbedPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "prelude.benchmark", matches = "true")
class HybridRetrievalCapacityTest {

    private static final int DOCUMENT_COUNT = 5_000;
    private static final int QUERY_COUNT = 300;
    private static final int WARMUP_COUNT = 50;
    private static final int VECTOR_DIMENSIONS = 64;

    @Test
    void measuresDeterministicHybridSearchCapacityAndRecall() {
        InMemoryRetrievalAdapter adapter = new InMemoryRetrievalAdapter(
            new HashEmbedding(), new MemoryChunkStore(), (scopeType, scopeId) -> List.of(), 0.7, 0.3
        );
        List<String> documents = documents();

        long indexStarted = System.nanoTime();
        adapter.index(RetrievalPort.SCOPE_RESUME, 1L, documents);
        long indexNanos = System.nanoTime() - indexStarted;

        for (int index = 0; index < WARMUP_COUNT; index++) {
            adapter.search(RetrievalPort.SCOPE_RESUME, 1L, query(index), 5);
        }

        long[] searchNanos = new long[QUERY_COUNT];
        int hits = 0;
        for (int index = 0; index < QUERY_COUNT; index++) {
            int documentIndex = (index * 17) % DOCUMENT_COUNT;
            long started = System.nanoTime();
            List<String> results = adapter.search(
                RetrievalPort.SCOPE_RESUME, 1L, query(documentIndex), 5
            );
            searchNanos[index] = System.nanoTime() - started;
            String expectedMarker = marker(documentIndex);
            if (results.stream().anyMatch(result -> result.contains(expectedMarker))) {
                hits++;
            }
        }

        Arrays.sort(searchNanos);
        double recallAtFive = hits / (double) QUERY_COUNT;
        System.out.printf(
            Locale.ROOT,
            "RETRIEVAL_CAPACITY documents=%d queries=%d dimensions=%d index_ms=%.3f "
                + "search_p50_ms=%.3f search_p95_ms=%.3f recall_at_5=%.4f "
                + "java=%s os=%s processors=%d%n",
            DOCUMENT_COUNT,
            QUERY_COUNT,
            VECTOR_DIMENSIONS,
            millis(indexNanos),
            millis(percentile(searchNanos, 0.50)),
            millis(percentile(searchNanos, 0.95)),
            recallAtFive,
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            Runtime.getRuntime().availableProcessors()
        );

        assertThat(recallAtFive).isEqualTo(1.0);
    }

    private List<String> documents() {
        List<String> documents = new ArrayList<>(DOCUMENT_COUNT);
        for (int index = 0; index < DOCUMENT_COUNT; index++) {
            documents.add(marker(index) + " java reliability architecture evidence " + (index % 97));
        }
        return documents;
    }

    private String query(int index) {
        return marker(index) + " java reliability";
    }

    private String marker(int index) {
        return "candidate" + String.format(Locale.ROOT, "%05d", index);
    }

    private double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private long percentile(long[] sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1);
        return sorted[index];
    }

    private static final class HashEmbedding implements EmbedPort {

        @Override
        public float[] embed(String text) {
            float[] vector = new float[VECTOR_DIMENSIONS];
            for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (!token.isBlank()) {
                    int hash = token.hashCode();
                    int slot = Math.floorMod(hash, vector.length);
                    vector[slot] += (hash & 1) == 0 ? 1.0f : -1.0f;
                }
            }
            return vector;
        }

        @Override
        public String modelVersion() {
            return "hash-embedding-v1";
        }
    }

    private static final class MemoryChunkStore implements RetrievalChunkStore {

        private final Map<String, List<StoredChunk>> chunks = new HashMap<>();

        @Override
        public List<StoredChunk> load(String scopeType, Long scopeId) {
            return chunks.getOrDefault(key(scopeType, scopeId), List.of());
        }

        @Override
        public void replace(String scopeType, Long scopeId, List<StoredChunk> replacement) {
            chunks.put(key(scopeType, scopeId), List.copyOf(replacement));
        }

        @Override
        public void delete(String scopeType, Long scopeId) {
            chunks.remove(key(scopeType, scopeId));
        }

        private String key(String scopeType, Long scopeId) {
            return scopeType + ':' + scopeId;
        }
    }
}
