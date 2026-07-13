package com.interview.platform.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.retrieval.persistence.RetrievalChunkMapper;
import com.interview.platform.llm.EmbedPort;
import com.interview.platform.retrieval.InMemoryVectorIndex;
import com.interview.platform.retrieval.TextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InMemoryRetrievalAdapter implements RetrievalPort {

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 50;

    private final EmbedPort embedPort;
    private final RetrievalChunkMapper retrievalChunkMapper;
    private final RetrievalSourcePort retrievalSourcePort;
    private final double vectorWeight;
    private final double keywordWeight;
    private final Map<ScopeKey, InMemoryVectorIndex> indices = new ConcurrentHashMap<>();

    public InMemoryRetrievalAdapter(
        EmbedPort embedPort,
        RetrievalChunkMapper retrievalChunkMapper,
        RetrievalSourcePort retrievalSourcePort,
        @Value("${prelude.retrieval.hybrid.vector-weight:0.7}") double vectorWeight,
        @Value("${prelude.retrieval.hybrid.keyword-weight:0.3}") double keywordWeight
    ) {
        this.embedPort = embedPort;
        this.retrievalChunkMapper = retrievalChunkMapper;
        this.retrievalSourcePort = retrievalSourcePort;
        double totalWeight = vectorWeight + keywordWeight;
        this.vectorWeight = totalWeight > 0 ? vectorWeight / totalWeight : 0.7;
        this.keywordWeight = totalWeight > 0 ? keywordWeight / totalWeight : 0.3;
    }

    @Override
    public synchronized void index(String scopeType, Long scopeId, List<String> documents) {
        ScopeKey key = new ScopeKey(scopeType, scopeId);
        List<String> chunks = splitDocuments(documents);
        persistChunks(key, chunks);
        indices.put(key, buildIndex(key, chunks));
        log.info("retrieval_indexed scopeType={} scopeId={} chunks={}", scopeType, scopeId, chunks.size());
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

        float[] queryVector;
        try {
            queryVector = embedPort.embed(query);
        } catch (Exception exception) {
            log.warn("retrieval_query_embedding_failed scopeType={} scopeId={}", scopeType, scopeId, exception);
            return List.of();
        }

        List<InMemoryVectorIndex.Entry> vectorResults = index.search(queryVector, topK * 2);
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        String[] queryWords = query.toLowerCase().split("\\s+|\\p{Punct}+");
        for (InMemoryVectorIndex.Entry entry : vectorResults) {
            double vectorScore = cosineSimilarity(queryVector, entry.vector());
            double keywordScore = keywordScore(queryWords, entry.text());
            scoredChunks.add(new ScoredChunk(
                entry.text(),
                vectorWeight * vectorScore + keywordWeight * keywordScore
            ));
        }
        scoredChunks.sort((left, right) -> Double.compare(right.score(), left.score()));
        return scoredChunks.stream().limit(topK).map(ScoredChunk::text).toList();
    }

    @Override
    public synchronized void invalidate(String scopeType, Long scopeId) {
        ScopeKey key = new ScopeKey(scopeType, scopeId);
        indices.remove(key);
        try {
            retrievalChunkMapper.delete(scopeQuery(key));
        } catch (Exception exception) {
            log.warn("retrieval_invalidate_persistence_failed scopeType={} scopeId={}", scopeType, scopeId, exception);
        }
    }

    private synchronized InMemoryVectorIndex getOrRebuildIndex(ScopeKey key) {
        InMemoryVectorIndex cached = indices.get(key);
        if (cached != null) {
            return cached;
        }

        List<String> chunks = loadPersistedChunks(key);
        String source = "persisted";
        if (chunks.isEmpty()) {
            source = "source";
            try {
                chunks = splitDocuments(retrievalSourcePort.loadDocuments(key.scopeType(), key.scopeId()));
                persistChunks(key, chunks);
            } catch (Exception exception) {
                log.warn("retrieval_rebuild_failed scopeType={} scopeId={}", key.scopeType(), key.scopeId(), exception);
                return null;
            }
        }
        if (chunks.isEmpty()) {
            return null;
        }

        InMemoryVectorIndex rebuilt = buildIndex(key, chunks);
        indices.put(key, rebuilt);
        log.info(
            "retrieval_rebuilt scopeType={} scopeId={} source={} chunks={}",
            key.scopeType(), key.scopeId(), source, chunks.size()
        );
        return rebuilt;
    }

    private InMemoryVectorIndex buildIndex(ScopeKey key, List<String> chunks) {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        for (String chunk : chunks) {
            try {
                index.add(chunk, embedPort.embed(chunk));
            } catch (Exception exception) {
                log.warn(
                    "retrieval_chunk_embedding_failed scopeType={} scopeId={} contentHash={}",
                    key.scopeType(), key.scopeId(), contentHash(chunk), exception
                );
            }
        }
        return index;
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

    private List<String> loadPersistedChunks(ScopeKey key) {
        try {
            List<RetrievalChunk> chunks = retrievalChunkMapper.selectList(
                scopeQuery(key).orderByAsc(RetrievalChunk::getOrdinal)
            );
            if (chunks == null) {
                return List.of();
            }
            return chunks.stream().map(RetrievalChunk::getContent).toList();
        } catch (Exception exception) {
            log.warn(
                "retrieval_persistence_read_failed scopeType={} scopeId={}",
                key.scopeType(), key.scopeId(), exception
            );
            return List.of();
        }
    }

    private void persistChunks(ScopeKey key, List<String> chunks) {
        try {
            retrievalChunkMapper.delete(scopeQuery(key));
            for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
                RetrievalChunk chunk = new RetrievalChunk();
                chunk.setScopeType(key.scopeType());
                chunk.setScopeId(key.scopeId());
                chunk.setOrdinal(ordinal);
                chunk.setContent(chunks.get(ordinal));
                chunk.setContentHash(contentHash(chunks.get(ordinal)));
                retrievalChunkMapper.insert(chunk);
            }
        } catch (Exception exception) {
            log.warn(
                "retrieval_persistence_write_failed scopeType={} scopeId={}",
                key.scopeType(), key.scopeId(), exception
            );
        }
    }

    private LambdaQueryWrapper<RetrievalChunk> scopeQuery(ScopeKey key) {
        return new LambdaQueryWrapper<RetrievalChunk>()
            .eq(RetrievalChunk::getScopeType, key.scopeType())
            .eq(RetrievalChunk::getScopeId, key.scopeId());
    }

    private double keywordScore(String[] queryWords, String text) {
        int matched = 0;
        int validWords = 0;
        String normalizedText = text.toLowerCase();
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
            leftNorm += Math.pow(left[index], 2);
            rightNorm += Math.pow(right[index], 2);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
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

    private record ScopeKey(String scopeType, Long scopeId) {
    }

    private record ScoredChunk(String text, double score) {
    }
}
