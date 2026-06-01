package com.interview.service.impl;

import com.interview.entity.InterviewSession;
import com.interview.entity.Resume;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.service.EmbeddingService;
import com.interview.service.SessionRagService;
import com.interview.util.InMemoryVectorIndex;
import com.interview.util.TextSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionRagServiceImpl implements SessionRagService {

    private final EmbeddingService embeddingService;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ResumeMapper resumeMapper;
    
    private final Map<Long, InMemoryVectorIndex> sessionIndices = new ConcurrentHashMap<>();

    @Override
    public synchronized void indexSession(Long sessionId, String resumeText, String jdText) {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        
        List<String> allChunks = new ArrayList<>();
        if (resumeText != null && !resumeText.isBlank()) {
            allChunks.addAll(TextSplitter.splitText(resumeText, 512, 50));
        }
        if (jdText != null && !jdText.isBlank()) {
            allChunks.addAll(TextSplitter.splitText(jdText, 512, 50));
        }

        for (String chunk : allChunks) {
            try {
                float[] vector = embeddingService.getEmbedding(chunk);
                index.add(chunk, vector);
            } catch (Exception exception) {
                log.warn("Failed to index chunk for session {}: {}", sessionId, chunk, exception);
            }
        }
        sessionIndices.put(sessionId, index);
        log.info("Successfully indexed {} chunks for session {}", allChunks.size(), sessionId);
    }

    @Override
    public List<String> searchTopChunks(Long sessionId, String query, int topK) {
        InMemoryVectorIndex index = getOrLoadIndex(sessionId);
        if (index == null) {
            return List.of();
        }
        
        float[] queryVector;
        try {
            queryVector = embeddingService.getEmbedding(query);
        } catch (Exception exception) {
            log.error("Failed to get embedding for query: {}", query, exception);
            return List.of();
        }

        List<InMemoryVectorIndex.Entry> vectorResults = index.search(queryVector, topK * 2);
        
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        String[] queryWords = query.toLowerCase().split("\\s+|\\p{Punct}+");

        for (InMemoryVectorIndex.Entry entry : vectorResults) {
            double vectorSim = cosineSimilarity(queryVector, entry.vector());
            
            int matched = 0;
            int validQueryWords = 0;
            for (String word : queryWords) {
                if (word.length() >= 2) {
                    validQueryWords++;
                    if (entry.text().toLowerCase().contains(word)) {
                        matched++;
                    }
                }
            }
            double keywordScore = validQueryWords > 0 ? (double) matched / validQueryWords : 0.0;
            
            double hybridScore = 0.7 * vectorSim + 0.3 * keywordScore;
            scoredChunks.add(new ScoredChunk(entry.text(), hybridScore));
        }

        scoredChunks.sort((a, b) -> Double.compare(b.score, a.score));
        
        List<String> topResults = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scoredChunks.size()); i++) {
            topResults.add(scoredChunks.get(i).text);
        }
        return topResults;
    }

    private synchronized InMemoryVectorIndex getOrLoadIndex(Long sessionId) {
        InMemoryVectorIndex index = sessionIndices.get(sessionId);
        if (index != null) {
            return index;
        }
        
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session != null) {
            Resume resume = resumeMapper.selectById(session.getResumeId());
            String resumeText = resume != null ? resume.getRawText() : null;
            String jdText = session.getJdText();
            indexSession(sessionId, resumeText, jdText);
            return sessionIndices.get(sessionId);
        }
        return null;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < Math.min(v1.length, v2.length); i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(String text, double score) {}
}
