package com.interview.service.impl;

import com.interview.entity.InterviewSession;
import com.interview.entity.Resume;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.service.EmbeddingService;
import com.interview.util.InMemoryVectorIndex;
import com.interview.util.TextSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionRagServiceImplTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private ResumeMapper resumeMapper;

    @InjectMocks
    private SessionRagServiceImpl sessionRagService;

    @Test
    void testTextSplitter() {
        String shortText = "Hello World";
        List<String> chunks = TextSplitter.splitText(shortText, 5, 2);
        // "Hello" (0 to 5)
        // "lo Wo" (5 - 2 = 3 to 8) -> "lo Wo"
        // "World" (8 - 2 = 6 to 11) -> "World"
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).isEqualTo("Hello");
        
        // Size 512, Overlap 50
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("a");
        }
        List<String> largeChunks = TextSplitter.splitText(sb.toString(), 512, 50);
        assertThat(largeChunks.size()).isEqualTo(3);
        assertThat(largeChunks.get(0).length()).isEqualTo(512);
    }

    @Test
    void testInMemoryVectorIndexSimilarity() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        float[] v1 = new float[]{1.0f, 0.0f};
        float[] v2 = new float[]{0.8f, 0.6f};
        float[] v3 = new float[]{0.0f, 1.0f};

        index.add("V1 Text", v1);
        index.add("V2 Text", v2);
        index.add("V3 Text", v3);

        float[] query = new float[]{1.0f, 0.1f};
        List<InMemoryVectorIndex.Entry> results = index.search(query, 2);
        
        assertThat(results).hasSize(2);
        assertThat(results.get(0).text()).isEqualTo("V1 Text");
        assertThat(results.get(1).text()).isEqualTo("V2 Text");
    }

    @Test
    void testHybridSearchAndKeywordBoosting() {
        Long sessionId = 100L;
        String resumeText = "Experienced backend developer.";
        String jdText = "Looking for a Spring Cloud expert with Redis experience.";

        // Mock embedding return vectors (1536 dims)
        float[] mockVec = new float[1536];
        mockVec[0] = 1.0f;
        when(embeddingService.getEmbedding(anyString())).thenReturn(mockVec);

        // Execute indexing
        sessionRagService.indexSession(sessionId, resumeText, jdText);

        // Perform search. The query includes words that match parts of the text
        // Hybrid Search = 0.7 * CosineSimilarity + 0.3 * KeywordScore
        List<String> results = sessionRagService.searchTopChunks(sessionId, "Redis Spring Cloud", 5);

        assertThat(results).isNotEmpty();
        // Since we mocked all embeddings to be the same, the keyword score will differentiate them.
        // JD chunk contains "Redis" and "Spring Cloud", so it must rank higher.
        assertThat(results.get(0)).contains("Redis").contains("Spring Cloud");
    }

    @Test
    void testJdHighFrequencyKeywordsHit() {
        Long sessionId = 200L;
        // Construct a JD with target high frequency keywords
        String jdText = "Job Description: We are looking for a Senior Java Developer. " +
                "Core requirements: hands-on experience with Redis caching, " +
                "building microservices using Spring Cloud, and optimizing database performance.";

        // We use Mockito to mock database calls for getOrLoadIndex (lazy loading check)
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setResumeId(1L);
        session.setJdText(jdText);

        Resume resume = new Resume();
        resume.setId(1L);
        resume.setRawText("My resume with some Java skills.");

        when(interviewSessionMapper.selectById(sessionId)).thenReturn(session);
        when(resumeMapper.selectById(1L)).thenReturn(resume);

        // Deterministic mock vector to simulate query and doc embeddings matching
        float[] mockVec = new float[1536];
        mockVec[0] = 0.5f;
        when(embeddingService.getEmbedding(anyString())).thenReturn(mockVec);

        // Querying for related items
        List<String> searchResults = sessionRagService.searchTopChunks(sessionId, "Tell me about your experience with Redis and Spring Cloud", 5);

        // Assert 100% hit rate of high frequency keywords in top retrieved text pieces
        boolean hitsRedis = false;
        boolean hitsSpringCloud = false;
        
        for (String chunk : searchResults) {
            if (chunk.toLowerCase().contains("redis")) {
                hitsRedis = true;
            }
            if (chunk.toLowerCase().contains("spring cloud")) {
                hitsSpringCloud = true;
            }
        }

        assertThat(hitsRedis).as("Retrieved RAG context should hit 'Redis' keyword").isTrue();
        assertThat(hitsSpringCloud).as("Retrieved RAG context should hit 'Spring Cloud' keyword").isTrue();
    }
}
