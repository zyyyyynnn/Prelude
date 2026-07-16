package com.interview.platform.retrieval;

import com.interview.platform.llm.EmbedPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class InMemoryRetrievalAdapter implements RetrievalPort {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 50;
    private static final int LOCK_STRIPES = 64;

    private final EmbedPort embedPort;
    private final RetrievalChunkStore chunkStore;
    private final RetrievalSourcePort retrievalSourcePort;
    private final double vectorWeight;
    private final double keywordWeight;
    private final Map<ScopeKey, InMemoryVectorIndex> indices = new ConcurrentHashMap<>();
    private final ReentrantLock[] scopeLocks = new ReentrantLock[LOCK_STRIPES];

    public InMemoryRetrievalAdapter(
        EmbedPort embedPort,
        RetrievalChunkStore chunkStore,
        RetrievalSourcePort retrievalSourcePort,
        @Value("${prelude.retrieval.hybrid.vector-weight:0.7}") double vectorWeight,
        @Value("${prelude.retrieval.hybrid.keyword-weight:0.3}") double keywordWeight
    ) {
        this.embedPort = embedPort;
        this.chunkStore = chunkStore;
        this.retrievalSourcePort = retrievalSourcePort;
        double totalWeight = vectorWeight + keywordWeight;
        this.vectorWeight = totalWeight > 0 ? vectorWeight / totalWeight : 0.7;
        this.keywordWeight = totalWeight > 0 ? keywordWeight / totalWeight : 0.3;
        for (int index = 0; index < scopeLocks.length; index++) {
            scopeLocks[index] = new ReentrantLock();
        }
    }

    @Override
    public void index(String scopeType, Long scopeId, List<String> documents) {
        ScopeKey key = new ScopeKey(scopeType, scopeId);
        withScopeLock(key, () -> {
            List<RetrievalChunkStore.StoredChunk> snapshot = buildSnapshot(key, splitDocuments(documents));
            persistSnapshot(key, snapshot);
            indices.put(key, toIndex(snapshot));
            log.info(
                "retrieval_indexed scopeType={} scopeId={} chunks={} embedded={}",
                scopeType, scopeId, snapshot.size(), embeddedCount(snapshot)
            );
        });
    }

    @Override
    public List<String> search(String scopeType, Long scopeId, String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        ScopeKey key = new ScopeKey(scopeType, scopeId);
        InMemoryVectorIndex index = getOrRebuildIndex(key);
        if (index == null) {
            return List.of();
        }

        float[] queryVector = null;
        try {
            queryVector = embedPort.embed(query);
        } catch (RuntimeException exception) {
            log.warn(
                "retrieval_query_embedding_failed scopeType={} scopeId={} fallback=keyword",
                scopeType, scopeId
            );
        }

        String[] queryWords = tokenize(query);
        List<ScoredChunk> scored = new ArrayList<>();
        for (InMemoryVectorIndex.Entry entry : index.entries()) {
            double keywordScore = keywordScore(queryWords, entry.text());
            double score;
            if (queryVector == null) {
                score = keywordScore;
            } else {
                double vectorScore = entry.vector() == null
                    ? 0.0
                    : normalizeCosine(cosineSimilarity(queryVector, entry.vector()));
                score = vectorWeight * vectorScore + keywordWeight * keywordScore;
            }
            if (score > 0) {
                scored.add(new ScoredChunk(entry.text(), score, keywordScore));
            }
        }
        scored.sort((left, right) -> {
            int scoreOrder = Double.compare(right.score(), left.score());
            return scoreOrder != 0 ? scoreOrder : Double.compare(right.keywordScore(), left.keywordScore());
        });
        return scored.stream().limit(topK).map(ScoredChunk::text).toList();
    }

    @Override
    public void invalidate(String scopeType, Long scopeId) {
        ScopeKey key = new ScopeKey(scopeType, scopeId);
        withScopeLock(key, () -> {
            try {
                chunkStore.delete(scopeType, scopeId);
                indices.remove(key);
            } catch (RuntimeException exception) {
                log.warn("retrieval_invalidate_failed scopeType={} scopeId={}", scopeType, scopeId);
            }
        });
    }

    private InMemoryVectorIndex getOrRebuildIndex(ScopeKey key) {
        InMemoryVectorIndex cached = indices.get(key);
        if (cached != null) {
            return cached;
        }

        return withScopeLock(key, () -> {
            InMemoryVectorIndex concurrent = indices.get(key);
            if (concurrent != null) {
                return concurrent;
            }

            List<RetrievalChunkStore.StoredChunk> stored = loadPersistedChunks(key);
            String source = "persisted";
            if (stored.isEmpty()) {
                source = "source";
                try {
                    stored = buildSnapshot(
                        key,
                        splitDocuments(retrievalSourcePort.loadDocuments(key.scopeType(), key.scopeId()))
                    );
                    persistSnapshot(key, stored);
                } catch (RuntimeException exception) {
                    log.warn("retrieval_rebuild_failed scopeType={} scopeId={}", key.scopeType(), key.scopeId());
                    return null;
                }
            } else if (requiresEmbeddingRefresh(stored)) {
                stored = buildSnapshot(key, stored.stream().map(RetrievalChunkStore.StoredChunk::content).toList());
                persistSnapshot(key, stored);
                source = "persisted-refreshed";
            }
            if (stored.isEmpty()) {
                return null;
            }

            InMemoryVectorIndex rebuilt = toIndex(stored);
            indices.put(key, rebuilt);
            log.info(
                "retrieval_rebuilt scopeType={} scopeId={} source={} chunks={} embedded={}",
                key.scopeType(), key.scopeId(), source, stored.size(), embeddedCount(stored)
            );
            return rebuilt;
        });
    }

    private List<RetrievalChunkStore.StoredChunk> buildSnapshot(ScopeKey key, List<String> chunks) {
        String modelVersion = currentEmbeddingModel();
        List<RetrievalChunkStore.StoredChunk> snapshot = new ArrayList<>();
        for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
            String chunk = chunks.get(ordinal);
            float[] embedding = null;
            try {
                embedding = embedPort.embed(chunk);
            } catch (RuntimeException exception) {
                log.warn(
                    "retrieval_chunk_embedding_failed scopeType={} scopeId={} contentHash={} fallback=keyword",
                    key.scopeType(), key.scopeId(), contentHash(chunk)
                );
            }
            snapshot.add(new RetrievalChunkStore.StoredChunk(
                ordinal,
                chunk,
                contentHash(chunk),
                embedding == null ? null : modelVersion,
                embedding
            ));
        }
        return List.copyOf(snapshot);
    }

    private boolean requiresEmbeddingRefresh(List<RetrievalChunkStore.StoredChunk> chunks) {
        String currentModel = currentEmbeddingModel();
        return chunks.stream().anyMatch(chunk ->
            chunk.embedding() == null || !currentModel.equals(chunk.embeddingModel())
        );
    }

    private InMemoryVectorIndex toIndex(List<RetrievalChunkStore.StoredChunk> chunks) {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        chunks.forEach(chunk -> index.add(new InMemoryVectorIndex.Entry(
            chunk.content(), chunk.contentHash(), chunk.embeddingModel(), chunk.embedding()
        )));
        return index;
    }

    private List<RetrievalChunkStore.StoredChunk> loadPersistedChunks(ScopeKey key) {
        try {
            return chunkStore.load(key.scopeType(), key.scopeId());
        } catch (RuntimeException exception) {
            log.warn("retrieval_persistence_read_failed scopeType={} scopeId={}", key.scopeType(), key.scopeId());
            return List.of();
        }
    }

    private void persistSnapshot(ScopeKey key, List<RetrievalChunkStore.StoredChunk> chunks) {
        try {
            chunkStore.replace(key.scopeType(), key.scopeId(), chunks);
        } catch (RuntimeException exception) {
            log.warn("retrieval_persistence_write_failed scopeType={} scopeId={}", key.scopeType(), key.scopeId());
        }
    }

    private List<String> splitDocuments(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (String document : documents) {
            if (document != null && !document.isBlank()) {
                chunks.addAll(TextSplitter.splitText(document, CHUNK_SIZE, CHUNK_OVERLAP));
            }
        }
        return chunks;
    }

    private String[] tokenize(String query) {
        return query.toLowerCase(Locale.ROOT).split("\\s+|\\p{Punct}+");
    }

    private double keywordScore(String[] queryWords, String text) {
        int matched = 0;
        int validWords = 0;
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String word : queryWords) {
            if (word.length() >= 2) {
                validWords++;
                if (normalizedText.contains(word)) {
                    matched++;
                }
            }
        }
        return validWords == 0 ? 0.0 : (double) matched / validWords;
    }

    private double cosineSimilarity(float[] left, float[] right) {
        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < Math.min(left.length, right.length); index++) {
            dotProduct += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double normalizeCosine(double value) {
        return Math.max(0.0, Math.min(1.0, (value + 1.0) / 2.0));
    }

    private String contentHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long embeddedCount(List<RetrievalChunkStore.StoredChunk> chunks) {
        return chunks.stream().filter(chunk -> chunk.embedding() != null).count();
    }

    private String currentEmbeddingModel() {
        String modelVersion = embedPort.modelVersion();
        return modelVersion == null || modelVersion.isBlank() ? "unknown" : modelVersion;
    }

    private ReentrantLock lockFor(ScopeKey key) {
        return scopeLocks[Math.floorMod(key.hashCode(), scopeLocks.length)];
    }

    private void withScopeLock(ScopeKey key, Runnable action) {
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    private <T> T withScopeLock(ScopeKey key, java.util.function.Supplier<T> action) {
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private record ScopeKey(String scopeType, Long scopeId) {
    }

    private record ScoredChunk(String text, double score, double keywordScore) {
    }
}
