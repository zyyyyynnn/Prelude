package com.interview.platform.retrieval.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.platform.retrieval.RetrievalChunkStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MybatisRetrievalChunkStore implements RetrievalChunkStore {

    private final RetrievalChunkMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<StoredChunk> load(String scopeType, Long scopeId) {
        List<RetrievalChunk> rows = mapper.selectList(scopeQuery(scopeType, scopeId)
            .orderByAsc(RetrievalChunk::getOrdinal));
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
            .map(row -> new StoredChunk(
                row.getOrdinal(),
                row.getContent(),
                row.getContentHash(),
                row.getEmbeddingModel(),
                parseEmbedding(row)
            ))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replace(String scopeType, Long scopeId, List<StoredChunk> chunks) {
        mapper.delete(scopeQuery(scopeType, scopeId));
        for (StoredChunk stored : chunks) {
            RetrievalChunk row = new RetrievalChunk();
            row.setScopeType(scopeType);
            row.setScopeId(scopeId);
            row.setOrdinal(stored.ordinal());
            row.setContent(stored.content());
            row.setContentHash(stored.contentHash());
            row.setEmbeddingModel(stored.embeddingModel());
            row.setEmbeddingDimensions(stored.embedding() == null ? null : stored.embedding().length);
            row.setEmbeddingJson(serializeEmbedding(stored.embedding()));
            mapper.insert(row);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String scopeType, Long scopeId) {
        mapper.delete(scopeQuery(scopeType, scopeId));
    }

    private LambdaQueryWrapper<RetrievalChunk> scopeQuery(String scopeType, Long scopeId) {
        return new LambdaQueryWrapper<RetrievalChunk>()
            .eq(RetrievalChunk::getScopeType, scopeType)
            .eq(RetrievalChunk::getScopeId, scopeId);
    }

    private String serializeEmbedding(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize retrieval embedding", exception);
        }
    }

    private float[] parseEmbedding(RetrievalChunk row) {
        if (row.getEmbeddingJson() == null || row.getEmbeddingJson().isBlank()) {
            return null;
        }
        try {
            float[] embedding = objectMapper.readValue(row.getEmbeddingJson(), float[].class);
            if (row.getEmbeddingDimensions() != null && row.getEmbeddingDimensions() != embedding.length) {
                throw new IllegalArgumentException("embedding dimension mismatch");
            }
            return embedding;
        } catch (Exception exception) {
            log.warn(
                "retrieval_embedding_snapshot_invalid scopeType={} scopeId={} ordinal={}",
                row.getScopeType(), row.getScopeId(), row.getOrdinal()
            );
            return null;
        }
    }
}
