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
import com.prelude.llm.api.LlmPort.ToolBinding;
import com.prelude.llm.api.LlmPort.Usage;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sole transport-retry owner for model execution. Provider SDK retries are
 * disabled by SpringAiModelFactory. Fallback stays within the immutable
 * provider/credential/endpoint snapshot, and streaming is never replayed
 * after the first user-visible delta.
 */
@Slf4j
@Service
public class ModelExecutionService {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(120);

    private final SpringAiModelFactory modelFactory;
    private final ModelExecutionSnapshotService snapshotService;
    private final ModelProfileService profileService;
    private final ModelCapabilityCatalog capabilityCatalog;
    private final RetryRegistry retryRegistry;
    private final int maxTransportAttempts;

    public ModelExecutionService(
        SpringAiModelFactory modelFactory,
        ModelExecutionSnapshotService snapshotService,
        ModelProfileService profileService,
        ModelCapabilityCatalog capabilityCatalog,
        @Value("${prelude.llm.transport-retry-max-attempts:3}") int maxTransportAttempts
    ) {
        this.modelFactory = modelFactory;
        this.snapshotService = snapshotService;
        this.profileService = profileService;
        this.capabilityCatalog = capabilityCatalog;
        this.maxTransportAttempts = Math.max(1, maxTransportAttempts);
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(this.maxTransportAttempts)
            .intervalFunction(attempt -> Duration.ofSeconds(2L * attempt).toMillis())
            .retryOnException(this::isTransientFailure)
            .failAfterMaxAttempts(true)
            .build();
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    public CompletionResult complete(ModelExecutionRequest request) {
        ModelExecutionSnapshot snapshot = snapshotService.require(request.snapshotId());
        String apiKey = profileService.resolveApiKey(snapshot.getAccountId(), snapshot.getCredentialId());
        RuntimeException lastTransient = null;

        for (String model : executionCandidates(snapshot)) {
            ModelExecutionSnapshot effective = withModel(snapshot, model);
            validateRequest(effective, request, false);
            ChatModel chatModel = modelFactory.chatModel(effective, apiKey);
            List<ToolCallback> toolCallbacks = toToolCallbacks(request.tools());
            Prompt prompt = prompt(request, modelFactory.requestOptions(effective, request.responseMode()));
            try {
                Retry retry = retryRegistry.retry("llm-transport-" + effective.getProvider() + "-" + model);
                ChatResponse response = retry.executeSupplier(() -> call(chatModel, prompt, toolCallbacks));
                return new CompletionResult(extractContent(response), usageOf(effective, request, response));
            } catch (RuntimeException failure) {
                if (!isTransientFailure(failure)) {
                    throw mapFailure(failure);
                }
                lastTransient = failure;
                log.warn("Model execution exhausted transport retry for model {} (snapshot {})",
                    model, effective.getId());
            }
        }
        throw mapFailure(lastTransient == null
            ? new IllegalStateException("No executable model candidate")
            : lastTransient);
    }

    public void stream(ModelExecutionRequest request, StreamSink sink) {
        ModelExecutionSnapshot snapshot = snapshotService.require(request.snapshotId());
        String apiKey = profileService.resolveApiKey(snapshot.getAccountId(), snapshot.getCredentialId());
        RuntimeException lastTransient = null;

        for (String model : executionCandidates(snapshot)) {
            ModelExecutionSnapshot effective = withModel(snapshot, model);
            validateRequest(effective, request, true);
            ChatModel chatModel = modelFactory.chatModel(effective, apiKey);
            Prompt prompt = prompt(request, modelFactory.requestOptions(effective, request.responseMode()));
            AtomicBoolean emitted = new AtomicBoolean(false);
            AtomicReference<ChatResponse> latest = new AtomicReference<>();
            try {
                Flux<ChatResponse> execution = chatModel.stream(prompt)
                    .doOnNext(response -> {
                        latest.set(response);
                        String delta = extractDelta(response);
                        if (delta != null && !delta.isEmpty()) {
                            emitted.set(true);
                            sink.onNext(delta);
                        }
                    });
                if (maxTransportAttempts > 1) {
                    execution = execution.retryWhen(reactor.util.retry.Retry
                        .max(maxTransportAttempts - 1L)
                        .filter(failure -> !emitted.get() && isTransientFailure(failure))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
                }
                execution.blockLast(STREAM_TIMEOUT);
                sink.onUsage(usageOf(effective, request, latest.get()));
                return;
            } catch (RuntimeException failure) {
                if (emitted.get()) {
                    throw mapFailure(failure);
                }
                if (!isTransientFailure(failure)) {
                    throw mapFailure(failure);
                }
                lastTransient = failure;
                log.warn("Model stream exhausted transport retry before first delta for model {} (snapshot {})",
                    model, effective.getId());
            }
        }
        throw mapFailure(lastTransient == null
            ? new IllegalStateException("No executable model candidate")
            : lastTransient);
    }

    private ChatResponse call(ChatModel chatModel, Prompt prompt, List<ToolCallback> toolCallbacks) {
        if (toolCallbacks.isEmpty()) {
            return chatModel.call(prompt);
        }
        return ChatClient.builder(chatModel)
            .defaultTools(toolCallbacks.toArray())
            .build()
            .prompt(prompt)
            .call()
            .chatResponse();
    }

    private List<ToolCallback> toToolCallbacks(List<ToolBinding> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream().map(binding -> (ToolCallback) new ToolCallback() {
            private final ToolDefinition definition = ToolDefinition.builder()
                .name(binding.name())
                .description(binding.description())
                .inputSchema(binding.inputSchema())
                .build();

            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String arguments) {
                return binding.handler().call(arguments);
            }
        }).toList();
    }

    private void validateRequest(ModelExecutionSnapshot snapshot, ModelExecutionRequest request, boolean streaming) {
        if (request.responseMode() == null) {
            throw BusinessException.badRequest("模型输出模式不能为空");
        }
        var capability = capabilityCatalog.capability(snapshot.getProvider(), snapshot.getModel());
        if (streaming && !capability.streaming()) {
            throw BusinessException.badRequest("所选模型不支持流式输出");
        }
        if (request.responseMode() == LlmPort.ResponseMode.JSON && !capability.structuredOutput()) {
            throw BusinessException.badRequest("所选模型不支持结构化输出");
        }
        if (request.attachments() != null && !request.attachments().isEmpty() && !capability.vision()) {
            throw BusinessException.badRequest("所选模型不支持图像输入");
        }
        if (request.tools() != null && !request.tools().isEmpty() && !capability.toolCalling()) {
            throw BusinessException.badRequest("所选模型不支持工具调用");
        }
    }

    private List<String> executionCandidates(ModelExecutionSnapshot snapshot) {
        List<String> candidates = new ArrayList<>();
        candidates.add(snapshot.getModel());
        for (String model : parseFallback(snapshot.getFallbackModelsJson())) {
            if (!candidates.contains(model)) {
                candidates.add(model);
            }
        }
        return List.copyOf(candidates);
    }

    private List<String> parseFallback(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return new tools.jackson.databind.ObjectMapper()
                .readValue(json, new tools.jackson.core.type.TypeReference<List<String>>() {
                });
        } catch (Exception exception) {
            throw new IllegalStateException("Frozen fallback model list is invalid", exception);
        }
    }

    private Prompt prompt(ModelExecutionRequest request, ChatOptions options) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (Message message : request.messages()) {
            switch (message.role()) {
                case "system" -> messages.add(new SystemMessage(message.content()));
                case "assistant" -> messages.add(new AssistantMessage(message.content()));
                default -> messages.add(toUserMessage(message.content(), request.attachments()));
            }
        }
        return new Prompt(messages, options);
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
        return UserMessage.builder().text(content).media(media).build();
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
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private Usage usageOf(ModelExecutionSnapshot snapshot, ModelExecutionRequest request, ChatResponse response) {
        Long input = null;
        Long output = null;
        Long total = null;
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            input = usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
            output = usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue();
            total = usage.getTotalTokens() == null ? null : usage.getTotalTokens().longValue();
        }
        return new Usage(snapshot.getId(), request.purpose(), snapshot.getProvider(), snapshot.getModel(),
            input, output, total);
    }

    private boolean isTransientFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof NonTransientAiException || current instanceof BusinessException) {
                return false;
            }
            if (current instanceof TransientAiException
                || current instanceof com.openai.errors.OpenAIIoException
                || current instanceof com.anthropic.errors.AnthropicIoException
                || current instanceof SocketTimeoutException
                || current instanceof java.net.http.HttpTimeoutException
                || current instanceof TimeoutException
                || current instanceof ConnectException) {
                return true;
            }
            if (current instanceof com.openai.errors.OpenAIServiceException service) {
                return service.statusCode() == 429 || service.statusCode() >= 500;
            }
            if (current instanceof com.anthropic.errors.AnthropicServiceException service) {
                return service.statusCode() == 429 || service.statusCode() >= 500;
            }
        }
        return false;
    }

    private boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                || current instanceof java.net.http.HttpTimeoutException
                || current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private RuntimeException mapFailure(Throwable failure) {
        if (failure instanceof BusinessException business) {
            return business;
        }
        if (isTimeout(failure)) {
            return new LlmTimeoutException("模型服务调用超时，请稍后重试");
        }
        if (isTransientFailure(failure)) {
            return new LlmServerException("模型服务暂时不可用，请稍后重试");
        }
        if (contains(failure, NonTransientAiException.class)) {
            return BusinessException.badRequest("模型服务拒绝请求，请检查模型与凭据配置");
        }
        return BusinessException.badRequest("模型服务调用失败，请检查配置后重试");
    }

    private boolean contains(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
