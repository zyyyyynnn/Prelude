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
import com.prelude.llm.api.LlmUsageRecorded;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final ModelCapabilityJson capabilityJson;
    private final LlmTransportRetry transportRetry;
    private final ApplicationEventPublisher eventPublisher;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    public ModelExecutionService(
        SpringAiModelFactory modelFactory,
        ModelExecutionSnapshotService snapshotService,
        ModelProfileService profileService,
        ModelCapabilityJson capabilityJson,
        LlmTransportRetry transportRetry,
        ApplicationEventPublisher eventPublisher
    ) {
        this.modelFactory = modelFactory;
        this.snapshotService = snapshotService;
        this.profileService = profileService;
        this.capabilityJson = capabilityJson;
        this.transportRetry = transportRetry;
        this.eventPublisher = eventPublisher;
    }

    public CompletionResult complete(ModelExecutionRequest request) {
        ModelExecutionSnapshot snapshot = snapshotService.require(request.snapshotId());
        String apiKey = profileService.resolveApiKey(snapshot.getAccountId(), snapshot.getCredentialId());
        RuntimeException lastTransient = null;

        for (ModelExecutionSnapshot effective : executionCandidates(snapshot)) {
            validateRequest(effective, request, false);
            ChatModel chatModel = modelFactory.chatModel(effective, apiKey);
            List<ToolCallback> toolCallbacks = toToolCallbacks(request.tools());
            ChatOptions options = withToolCallbacks(
                modelFactory.requestOptions(effective, request.responseMode()), toolCallbacks);
            Prompt prompt = prompt(request, options);
            try {
                LogicalCompletion completion = completeLogicalTurn(chatModel, prompt, effective, request);
                publishUsage(effective, request, completion.usage());
                return new CompletionResult(completion.content(), completion.usage());
            } catch (InitialTransportFailure failure) {
                lastTransient = failure.transportFailure();
                log.warn("Model execution exhausted transport retry before tool execution for model {} (snapshot {})",
                    effective.getModel(), effective.getId());
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

        for (ModelExecutionSnapshot effective : executionCandidates(snapshot)) {
            validateRequest(effective, request, true);
            ChatModel chatModel = modelFactory.chatModel(effective, apiKey);
            Prompt prompt = prompt(request, modelFactory.requestOptions(effective, request.responseMode()));
            AtomicBoolean emitted = new AtomicBoolean(false);
            AtomicReference<ChatResponse> latest = new AtomicReference<>();
            try {
                Flux<ChatResponse> execution = chatModel.stream(prompt)
                    .doOnNext(response -> {
                        latest.set(response);
                        String delta = extractDelta(effective, response);
                        if (delta != null && !delta.isEmpty()) {
                            emitted.set(true);
                            sink.onNext(delta);
                        }
                    });
                if (transportRetry.maxAttempts() > 1) {
                    execution = execution.retryWhen(reactor.util.retry.Retry
                        .max(transportRetry.maxAttempts() - 1L)
                        .filter(failure -> !emitted.get() && transportRetry.isTransient(failure))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
                }
                execution.blockLast(STREAM_TIMEOUT);
                ChatResponse terminal = latest.get();
                if (hasProviderUsage(terminal)) {
                    publishUsage(effective, request, usageOf(effective, request, terminal));
                }
                return;
            } catch (RuntimeException failure) {
                if (emitted.get()) {
                    throw mapFailure(failure);
                }
                if (!transportRetry.isTransient(failure)) {
                    throw mapFailure(failure);
                }
                lastTransient = failure;
                log.warn("Model stream exhausted transport retry before first delta for model {} (snapshot {})",
                    effective.getModel(), effective.getId());
            }
        }
        throw mapFailure(lastTransient == null
            ? new IllegalStateException("No executable model candidate")
            : lastTransient);
    }

    private LogicalCompletion completeLogicalTurn(
        ChatModel chatModel,
        Prompt initialPrompt,
        ModelExecutionSnapshot snapshot,
        ModelExecutionRequest request
    ) {
        Prompt prompt = initialPrompt;
        UsageAccumulator usage = new UsageAccumulator();
        ChatResponse response;
        try {
            response = callModel(chatModel, prompt, snapshot);
        } catch (RuntimeException failure) {
            if (transportRetry.isTransient(failure)) {
                throw new InitialTransportFailure(failure);
            }
            throw mapFailure(failure);
        }
        usage.add(response);

        while (response.hasToolCalls()) {
            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(toolResult.conversationHistory(), prompt.getOptions());
            try {
                response = callModel(chatModel, prompt, snapshot);
            } catch (RuntimeException failure) {
                // A tool side effect has already committed. Never restart this logical
                // turn on a fallback model; only this continuation transport was retried.
                throw mapFailure(failure);
            }
            usage.add(response);
        }

        Usage logicalUsage = usage.toUsage(snapshot, request);
        return new LogicalCompletion(extractContent(snapshot, response), logicalUsage);
    }

    private ChatResponse callModel(ChatModel chatModel, Prompt prompt, ModelExecutionSnapshot snapshot) {
        return transportRetry.execute(
            "chat-" + snapshot.getProvider() + "-" + snapshot.getModel(),
            () -> chatModel.call(prompt));
    }

    private ChatOptions withToolCallbacks(ChatOptions options, List<ToolCallback> toolCallbacks) {
        if (toolCallbacks.isEmpty()) {
            return options;
        }
        if (!(options instanceof ToolCallingChatOptions toolOptions)) {
            throw BusinessException.badRequest("所选模型运行时不支持工具调用");
        }
        return toolOptions.mutate().toolCallbacks(toolCallbacks).build();
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
        if (streaming && request.tools() != null && !request.tools().isEmpty()) {
            throw BusinessException.badRequest("当前不支持流式工具调用");
        }
        ModelCapabilityResponse capability = frozenCapability(snapshot);
        ModelCapabilityResponse.ReasoningLevel reasoningLevel;
        try {
            reasoningLevel = ModelCapabilityResponse.ReasoningLevel.valueOf(snapshot.getReasoningLevel());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Frozen reasoning level is invalid", exception);
        }
        if (!capability.supportedReasoningLevels().contains(reasoningLevel)) {
            throw BusinessException.badRequest("所选模型不支持该思考深度");
        }
        if (streaming && !capability.streaming()) {
            throw BusinessException.badRequest("所选模型不支持流式输出");
        }
        if (request.attachments() != null && !request.attachments().isEmpty() && !capability.vision()) {
            throw BusinessException.badRequest("所选模型不支持图像输入");
        }
        if (request.tools() != null && !request.tools().isEmpty() && !capability.toolCalling()) {
            throw BusinessException.badRequest("所选模型不支持工具调用");
        }
    }

    private List<ModelExecutionSnapshot> executionCandidates(ModelExecutionSnapshot snapshot) {
        List<ModelExecutionSnapshot> candidates = new ArrayList<>();
        candidates.add(snapshot);
        for (ModelCapabilityResponse fallback : capabilityJson.readList(snapshot.getFallbackCapabilitiesJson())) {
            if (candidates.stream().noneMatch(candidate -> candidate.getModel().equals(fallback.model()))) {
                candidates.add(withModel(snapshot, fallback));
            }
        }
        return List.copyOf(candidates);
    }

    private ModelCapabilityResponse frozenCapability(ModelExecutionSnapshot snapshot) {
        ModelCapabilityResponse capability = capabilityJson.read(snapshot.getModelCapabilityJson());
        if (!snapshot.getProvider().equals(capability.provider()) || !snapshot.getModel().equals(capability.model())) {
            throw new IllegalStateException("Frozen model capability does not match the execution snapshot");
        }
        return capability;
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

    private ModelExecutionSnapshot withModel(ModelExecutionSnapshot snapshot, ModelCapabilityResponse capability) {
        ModelExecutionSnapshot copy = new ModelExecutionSnapshot();
        copy.setId(snapshot.getId());
        copy.setAccountId(snapshot.getAccountId());
        copy.setProfileId(snapshot.getProfileId());
        copy.setProvider(snapshot.getProvider());
        copy.setModel(capability.model());
        copy.setReasoningLevel(snapshot.getReasoningLevel());
        copy.setEffectiveParametersJson(snapshot.getEffectiveParametersJson());
        copy.setCapabilityVersion(snapshot.getCapabilityVersion());
        copy.setModelCapabilityJson(capabilityJson.write(capability));
        copy.setFallbackCapabilitiesJson(snapshot.getFallbackCapabilitiesJson());
        copy.setCredentialId(snapshot.getCredentialId());
        copy.setCustomEndpointUrl(snapshot.getCustomEndpointUrl());
        return copy;
    }

    private String extractContent(ModelExecutionSnapshot snapshot, ChatResponse response) {
        if (response == null) {
            throw BusinessException.badRequest("模型服务返回内容为空");
        }
        if ("anthropic-messages".equals(snapshot.getProvider())) {
            for (int index = response.getResults().size() - 1; index >= 0; index--) {
                var output = response.getResults().get(index).getOutput();
                if (output != null && isVisibleAnthropicOutput(output)
                    && output.getText() != null && !output.getText().isBlank()) {
                    return output.getText();
                }
            }
            throw BusinessException.badRequest("模型服务返回内容为空");
        }
        if (response.getResult() == null || response.getResult().getOutput() == null
            || response.getResult().getOutput().getText() == null) {
            throw BusinessException.badRequest("模型服务返回内容为空");
        }
        return response.getResult().getOutput().getText();
    }

    private String extractDelta(ModelExecutionSnapshot snapshot, ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        if ("anthropic-messages".equals(snapshot.getProvider())
            && !isVisibleAnthropicOutput(response.getResult().getOutput())) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private boolean isVisibleAnthropicOutput(AssistantMessage output) {
        var metadata = output.getMetadata();
        return !metadata.containsKey("thinking")
            && !metadata.containsKey("signature")
            && !metadata.containsKey("data");
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

    private boolean hasProviderUsage(ChatResponse response) {
        return response != null
            && response.getMetadata() != null
            && response.getMetadata().getUsage() != null;
    }

    private void publishUsage(ModelExecutionSnapshot snapshot, ModelExecutionRequest request, Usage usage) {
        LlmUsageRecorded event = new LlmUsageRecorded(
            snapshot.getAccountId(),
            snapshot.getId(),
            request.purpose(),
            request.promptId(),
            snapshot.getProvider(),
            snapshot.getModel(),
            usage.inputTokens(),
            usage.outputTokens(),
            usage.totalTokens(),
            Instant.now(),
            null
        );
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException listenerFailure) {
            log.warn("LLM usage listener failed for snapshot {}; business execution remains successful ({})",
                snapshot.getId(), listenerFailure.getClass().getSimpleName());
        }
    }

    private RuntimeException mapFailure(Throwable failure) {
        if (failure instanceof BusinessException business) {
            return business;
        }
        if (transportRetry.isTimeout(failure)) {
            return new LlmTimeoutException("模型服务调用超时，请稍后重试");
        }
        if (transportRetry.isTransient(failure)) {
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

    private record LogicalCompletion(String content, Usage usage) {
    }

    private static final class InitialTransportFailure extends RuntimeException {

        private InitialTransportFailure(RuntimeException transportFailure) {
            super(transportFailure);
        }

        private RuntimeException transportFailure() {
            return (RuntimeException) getCause();
        }
    }

    private static final class UsageAccumulator {

        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;

        void add(ChatResponse response) {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return;
            }
            var usage = response.getMetadata().getUsage();
            inputTokens = add(inputTokens, usage.getPromptTokens());
            outputTokens = add(outputTokens, usage.getCompletionTokens());
            totalTokens = add(totalTokens, usage.getTotalTokens());
        }

        Usage toUsage(ModelExecutionSnapshot snapshot, ModelExecutionRequest request) {
            return new Usage(
                snapshot.getId(), request.purpose(), snapshot.getProvider(), snapshot.getModel(),
                inputTokens, outputTokens, totalTokens);
        }

        private Long add(Long current, Number next) {
            if (next == null) {
                return current;
            }
            return (current == null ? 0L : current) + next.longValue();
        }
    }
}
