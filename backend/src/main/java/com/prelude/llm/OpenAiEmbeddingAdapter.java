package com.prelude.llm;

import com.prelude.BusinessException;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Retrieval embeddings through Spring AI's OpenAI embedding model, built
 * programmatically with the deployment credential. The system OpenAI key is
 * the only credential for this infrastructure path; there is no per-account
 * embedding BYOK scope today.
 */
@Service
public class OpenAiEmbeddingAdapter implements EmbedPort {

    private final OpenAiEmbeddingModel embeddingModel;
    private final String embeddingModelVersion;

    public OpenAiEmbeddingAdapter(
        @Value("${prelude.llm.provider.openai.base-url:https://api.openai.com/v1}") String baseUrl,
        @Value("${prelude.llm.provider.openai.api-key:}") String apiKey,
        @Value("${prelude.llm.provider.openai.embedding-model:text-embedding-3-small}") String embeddingModelVersion
    ) {
        this.embeddingModelVersion = embeddingModelVersion;
        this.embeddingModel = OpenAiEmbeddingModel.builder()
            .openAiClient(OpenAiSetup.setupSyncClient(
                baseUrl, null, com.openai.credential.BearerTokenCredential.create(apiKey),
                null, null, null, false, false, null,
                Duration.ofSeconds(60), 1, null, Map.of(),
                io.micrometer.observation.ObservationRegistry.NOOP,
                io.micrometer.core.instrument.Metrics.globalRegistry,
                List.of()))
            .metadataMode(MetadataMode.EMBED)
            .options(OpenAiEmbeddingOptions.builder().model(embeddingModelVersion).build())
            .build();
    }

    @Override
    public float[] embed(String text) {
        try {
            float[] vector = embeddingModel.embed(text);
            if (vector == null || vector.length == 0) {
                throw BusinessException.badRequest("Embedding 服务返回内容为空");
            }
            return vector;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("Embedding 调用失败，请稍后重试");
        }
    }

    @Override
    public String modelVersion() {
        return embeddingModelVersion;
    }
}
