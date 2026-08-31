package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.LlmServerException;
import com.prelude.LlmTimeoutException;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.LlmPort.Attachment;
import com.prelude.llm.api.LlmPort.CompletionResult;
import com.prelude.llm.api.LlmPort.Message;
import com.prelude.llm.api.LlmPort.ModelExecutionRequest;
import com.prelude.llm.api.LlmPort.StreamSink;
import com.prelude.llm.api.LlmPort.Usage;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes model calls against immutable snapshots through Spring AI. This is
 * the single owner of transport retry: Spring AI/provider SDK retries are
 * pinned to one attempt (see SpringAiModelFactory) and this layer applies a
 * bounded Resilience4j retry for transient failures only (timeout, 429,
 * selected 5xx). Semantic errors (4xx) are not retried. Fallback follows the
 * frozen snapshot's ordered model list and never crosses a provider or
 * credential boundary.
 */
@Slf4j
@Service
public class ModelExecutionService {

    private static final int MAX_TRANSPORT_ATTEMPTS = 3;
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(120);

    private final SpringAiModelFactory modelFactory;
    private final ModelExecutionSnapshotService snapshotService;
    private final ModelProfileService profileService;
    private final RetryRegistry retryRegistry;

    public ModelExecutionService(
        SpringAiModelFactory modelFactory,
        ModelExecutionSnapshotService snapshotService,
        ModelProfileService profileService
    ) {
        this.modelFactory = modelFactory;
        this.snapshotService = snapshotService;
        this.profileService = profileService;
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_TRANSPORT_ATTEMPTS)
            .intervalFunction(attempt -> Duration.ofSeconds(2L * attempt).toMillis())
            .retryExceptions(LlmTimeoutException.class, LlmServerException.class)
            .failAfterMaxAttempts(true)
            .build();
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    public CompletionResult complete(ModelExecutionRequest request) {
        ModelExecutionSnapshot snapshot = snapshotService.require(request.snapshotId());
        String apiKey = profileService.resolveApiKey(snapshot.getAccountId(), snapshot.getCredentialId());
        List<String> candidates = executionCandidates(snapshot);

        Optional<ChatOptions> jsonFormat = responseFormatRequested(request, snapshot);
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        LlmServerException last = null;
        for (String model : candidates) {
            ModelExecutionSnapshot effectiveSnapshot = withModel(snapshot, model);
            ChatModel chatModel = modelFactory.chatModel(effectiveSnapshot, apiKey);
            try {
                Retry retry = retryRegistry.retry("llm-transport");
                ChatResponse response = retry.executeSupplier(() ->
                    chatModel.call(prompt(request, effectiveSnapshot, jsonFormat)));
                String content = extractContent(response);
                usageRef.set(usageOf(effectiveSnapshot, request, response));
                return new CompletionResult(content, usageRef.get());
            } catch (LlmTimeoutException | LlmServerException transientFailure) {
                log.warn("Model execution attempt failed for model {} (snapshot {}): {}",
                    model, effectiveSnapshot.getId(), transientFailure.getMessage());
                last = asTransient(transientFailure);
            }
        }
        throw last != null ? last
            : BusinessException.badRequest("模型服务调用失败");
    }

    public void stream(ModelExecutionRequest request, StreamSink sink) {
        ModelExecutionSnapshot snapshot = snapshotService.require(request.snapshotId());
        String apiKey = profileService.resolveApiKey(snapshot.getAccountId(), snapshot.getCredentialId());
        List<String> candidates = executionCandidates(snapshot);
        Optional<ChatOptions> jsonFormat = Optional.empty();

        StringBuilder assembled = new StringBuilder();
        for (String model : candidates) {
            ModelExecutionSnapshot effective = withModel(snapshot, model);
            ChatModel chatModel = modelFactory.chatModel(effective, apiKey);
            try {
                Retry retry = retryRegistry.retry("llm-transport");
                Flux<ChatResponse> flux = retry.executeSupplier(() ->
                    chatModel.stream(prompt(request, effective, jsonFormat)));
                flux.doOnNext(response -> {
                        String delta = extractDelta(response);
                        if (delta != null && !delta.isEmpty()) {
                            assembled.append(delta);
                            sink.onNext(delta);
                        }
                    })
                    .blockLast(STREAM_TIMEOUT);
                sink.onUsage(usageOf(effective, request, null));
                return;
            } catch (LlmTimeoutException | LlmServerException transientFailure) {
                log.warn("Model stream attempt failed for model {} (snapshot {}): {}",
                    model, snapshot.getId(), transientFailure.getMessage());
            }
        }
        throw BusinessException.badRequest("模型服务调用失败");
    }

    /**
     * Fallback is profile behavior: the frozen ordered list, always within the
     * same provider and credential boundary. No list means one candidate —
     * the frozen primary model — and an explicit failure if it fails.
     */
    private List<String> executionCandidates(ModelExecutionSnapshot snapshot) {
        List<String> fallback = parseFallback(snapshot.getFallbackModelsJson());
        List<String> candidates = new ArrayList<>();
        candidates.add(snapshot.getModel());
        for (String model : fallback) {
            if (!candidates.contains(model)) {
                candidates.add(model);
            }
        }
        return candidates;
    }

    private List<String> parseFallback(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Prompt prompt(ModelExecutionRequest request, ModelExecutionSnapshot snapshot,
                          Optional<ChatOptions> extraOptions) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (Message message : request.messages()) {
            switch (message.role()) {
                case "system" -> messages.add(new SystemMessage(message.content()));
                case "assistant" -> messages.add(new AssistantMessage(message.content()));
                default -> messages.add(toUserMessage(message.content(), request.attachments()));
            }
        }
        return extraOptions
            .map(options -> new Prompt(messages, options))
            .orElseGet(() -> new Prompt(messages));
    }

    private org.springframework.ai.chat.messages.Message toUserMessage(String content, List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage(content);
        }
        List<Media> media = new ArrayList<>();
        for (Attachment attachment : attachments) {
            media.add(Media.builder()
                .mimeType(org.springframework.util.MimeTypeUtils.parseMimeType(attachment.mediaType()))
                .data(attachment.content())
                .name(attachment.fileName())
                .build());
        }
        return UserMessage.builder()
            .text(content)
            .media(media)
            .build();
    }

    private Optional<ChatOptions> responseFormatRequested(ModelExecutionRequest request,
                                                           ModelExecutionSnapshot snapshot) {
        return modelFactory.responseFormatJson(snapshot.getProvider());
    }

    private ModelExecutionSnapshot withModel(ModelExecutionSnapshot snapshot, String model) {
        if (model.equals(snapshot.getModel())) {
            return snapshot;
        }
        ModelExecutionSnapshot copy = new ModelExecutionSnapshot();
        copy.setId(snapshot.getId());
        copy.setAccountId(snapshot.getAccountId());
        copy.setProfileId(snapshot.getProfileId());
        copy.setProvider(snapshot.getProvider());
        copy.setModel(model);
        copy.setReasoningLevel(snapshot.getReasoningLevel());
        copy.setEffectiveParametersJson(snapshot.getEffectiveParametersJson());
        copy.setCapabilityVersion(snapshot.getCapabilityVersion());
        copy.setFallbackModelsJson(snapshot.getFallbackModelsJson());
        copy.setCredentialId(snapshot.getCredentialId());
        copy.setCustomEndpointUrl(snapshot.getCustomEndpointUrl());
        return copy;
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null
            || response.getResult().getOutput() == null
            || response.getResult().getOutput().getText() == null) {
            throw BusinessException.badRequest("模型服务返回内容为空");
        }
        return response.getResult().getOutput().getText();
    }

    private String extractDelta(ChatResponse response) {
        if (response == null || response.getResult() == null
            || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private Usage usageOf(ModelExecutionSnapshot snapshot, ModelExecutionRequest request, ChatResponse response) {
        Long input = null;
        Long output = null;
        Long total = null;
        if (response != null && response.getMetadata() != null
            && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            input = usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
            output = usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue();
            total = usage.getTotalTokens() == null ? null : usage.getTotalTokens().longValue();
        }
        return new Usage(
            snapshot.getId(),
            request.purpose(),
            snapshot.getProvider(),
            snapshot.getModel(),
            input,
            output,
            total
        );
    }

    private LlmServerException asTransient(Exception exception) {
        return exception instanceof LlmServerException server
            ? server
            : new LlmServerException(exception.getMessage());
    }
}
