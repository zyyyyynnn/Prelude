package com.interview.platform.retrieval;

import com.interview.platform.llm.EmbedPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRetrievalAnnotatedQueriesTest {

    private static final List<String> RESUME_CORPUS = List.of(
        """
        项目经历：负责电商秒杀系统的 Redis 预扣减与 RocketMQ 异步削峰。
        通过 Lua 脚本保证单节点库存扣减原子性，并结合 Hash Tag 将热点 SKU 路由到同一 Redis 节点。
        """,
        """
        工作经历：主导前端工程化体系建设，落地 Vite、TypeScript 严格模式与组件级 token 校验。
        推动 verify:ui、verify:tokens 与 verify:a11y 进入 CI blocking gate。
        """,
        """
        算法经历：构建简历/JD 混合检索链路，chunk 持久化 embedding 模型版本，查询 embedding 失败时退化到关键词路径。
        在 5,000 chunk 合成容量实验中验证 Recall@5 行为。
        """
    );

    private InMemoryRetrievalAdapter retrieval;

    @BeforeEach
    void setUp() {
        EmbedPort embedPort = mock(EmbedPort.class);
        when(embedPort.modelVersion()).thenReturn("annotated-fixture-v1");
        when(embedPort.embed(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT);
            float[] vector = new float[8];
            for (Map.Entry<String, Integer> entry : ANCHOR_VECTORS.entrySet()) {
                if (text.contains(entry.getKey())) {
                    vector[entry.getValue()] = 1.0f;
                }
            }
            return vector;
        });
        retrieval = new InMemoryRetrievalAdapter(
            embedPort,
            new MemoryChunkStore(),
            (scopeType, scopeId) -> List.of(),
            0.7,
            0.3
        );
        retrieval.index(RetrievalPort.SCOPE_RESUME, 1L, RESUME_CORPUS);
    }

    @Test
    void annotatedResumeQueriesReturnExpectedEvidenceChunks() {
        assertTopMatch("秒杀系统如何保证 Redis 预扣减原子性", "Lua 脚本");
        assertTopMatch("前端 CI 里有哪些 UI 质量门禁", "verify:a11y");
        assertTopMatch("检索 embedding 失败时会怎样退化", "退化到关键词");
    }

    private void assertTopMatch(String query, String expectedSnippet) {
        List<String> results = retrieval.search(RetrievalPort.SCOPE_RESUME, 1L, query, 1);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).contains(expectedSnippet);
    }

    private static final Map<String, Integer> ANCHOR_VECTORS = Map.of(
        "redis", 0,
        "lua", 1,
        "rocketmq", 2,
        "vite", 3,
        "typescript", 4,
        "verify", 5,
        "embedding", 6,
        "recall", 7
    );

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
