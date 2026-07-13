package com.interview.platform.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.platform.llm.api.LlmProviderResponse;
import com.interview.platform.llm.persistence.LlmProviderConfig;
import com.interview.identity.domain.User;
import com.interview.platform.llm.persistence.LlmProviderConfigMapper;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.platform.llm.LlmProviderRegistry;
import com.interview.platform.llm.LlmSelectionResolver;
import com.interview.platform.security.AesGcmEncryptor;
import com.interview.platform.realtime.RealtimePort;
import com.interview.shared.api.LlmServerException;
import com.interview.shared.api.LlmTimeoutException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class LlmRouter {

    private final UserMapper userMapper;
    private final LlmProviderConfigMapper llmProviderConfigMapper;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final ObjectMapper objectMapper;
    private final LlmProviderRegistry providerRegistry;
    private final LlmSelectionResolver selectionResolver;
    private final RealtimePort realtimePort;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public LlmRouter(
        UserMapper userMapper,
        LlmProviderConfigMapper llmProviderConfigMapper,
        AesGcmEncryptor aesGcmEncryptor,
        ObjectMapper objectMapper,
        List<LlmProvider> providers,
        RealtimePort realtimePort
    ) {
        this.userMapper = userMapper;
        this.llmProviderConfigMapper = llmProviderConfigMapper;
        this.aesGcmEncryptor = aesGcmEncryptor;
        this.objectMapper = objectMapper;
        this.providerRegistry = new LlmProviderRegistry(providers);
        this.selectionResolver = new LlmSelectionResolver(
            userMapper,
            llmProviderConfigMapper,
            objectMapper,
            providerRegistry
        );
        this.realtimePort = realtimePort;

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // 50% failure rate
            .slidingWindowSize(10)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .recordExceptions(IOException.class, LlmServerException.class, LlmTimeoutException.class)
            .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    @Autowired
    public LlmRouter(
        UserMapper userMapper,
        LlmProviderConfigMapper llmProviderConfigMapper,
        AesGcmEncryptor aesGcmEncryptor,
        ObjectMapper objectMapper,
        List<LlmProvider> providers,
        RealtimePort realtimePort,
        LlmProviderRegistry providerRegistry,
        LlmSelectionResolver selectionResolver
    ) {
        this.userMapper = userMapper;
        this.llmProviderConfigMapper = llmProviderConfigMapper;
        this.aesGcmEncryptor = aesGcmEncryptor;
        this.objectMapper = objectMapper;
        this.providerRegistry = providerRegistry;
        this.selectionResolver = selectionResolver;
        this.realtimePort = realtimePort;

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .recordExceptions(IOException.class, LlmServerException.class, LlmTimeoutException.class)
            .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    public List<LlmProviderResponse> listEnabledProviders() {
        return llmProviderConfigMapper.selectList(new LambdaQueryWrapper<LlmProviderConfig>()
                .eq(LlmProviderConfig::getEnabled, 1)
                .orderByAsc(LlmProviderConfig::getId))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public LlmSelection resolveCurrentUserSelection() {
        return resolveCurrentUserSelection(null);
    }

    public LlmSelection resolveCurrentUserSelection(String requestedModel) {
        return selectionResolver.resolveCurrentUserSelection(requestedModel);
    }

    public String chatCurrentUser(List<Map<String, String>> messages) {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(user.getLlmProvider());
        String model = normalizeModel(user.getLlmModel(), provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), model);
        String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
        return provider.chat(buildInvocation(providerConfig, model, apiKey, user, messages, null));
    }

    public void streamCurrentUser(List<Map<String, String>> messages, Consumer<String> onDelta) {
        User user = requireCurrentUser();
        LlmProvider provider = requireProvider(user.getLlmProvider());
        String model = normalizeModel(user.getLlmModel(), provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), model);
        String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
        provider.streamChat(buildInvocation(providerConfig, model, apiKey, user, messages, null), onDelta);
    }

    public String chatWithSnapshot(String providerKey, String model, List<Map<String, String>> messages) {
        return chatWithSnapshot(providerKey, model, messages, null);
    }

    /**
     * 用显式参数测试草稿配置，不读写用户表、不触发 fallback、不经过熔断器。
     * openai-compatible 用 baseUrl 归一化后的 chat/completions 地址。
     */
    public String chatWithExplicit(
        String providerKey,
        String model,
        String baseUrl,
        String apiKey,
        List<Map<String, String>> messages
    ) {
        return chatWithExplicit(providerKey, model, baseUrl, apiKey, messages, null, null);
    }

    public String chatWithExplicit(
        String providerKey,
        String model,
        String baseUrl,
        String apiKey,
        List<Map<String, String>> messages,
        Integer maxTokens,
        Map<String, Object> extraParams
    ) {
        LlmProvider provider = requireProvider(providerKey);
        String normalizedModel = normalizeModel(model, provider.defaultModel());
        LlmProviderConfig providerConfig = validateProviderSelection(providerKey, normalizedModel);
        String resolvedApiKey = resolveExplicitApiKey(provider, apiKey);
        return provider.chat(buildInvocationExplicit(
            providerConfig, normalizedModel, baseUrl, resolvedApiKey, messages, maxTokens, extraParams));
    }

    public String chatWithSnapshot(
        String providerKey,
        String model,
        List<Map<String, String>> messages,
        Map<String, Object> extraParams
    ) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(providerKey);
        try {
            return cb.executeSupplier(() -> {
                User user = requireCurrentUser();
                LlmProvider provider = requireProvider(providerKey);
                String normalizedModel = normalizeModel(model, provider.defaultModel());
                LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), normalizedModel);
                String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
                return provider.chat(buildInvocation(providerConfig, normalizedModel, apiKey, user, messages, extraParams));
            });
        } catch (Exception e) {
            log.warn("Call to LLM provider {} failed, attempting fallback routing", providerKey, e);
            if (e instanceof BusinessException && !(e instanceof LlmServerException || e instanceof LlmTimeoutException)) {
                throw (BusinessException) e;
            }
            return executeFallbackChat(providerKey, model, messages, extraParams);
        }
    }

    public void streamWithSnapshot(String providerKey, String model, List<Map<String, String>> messages, Consumer<String> onDelta) {
        streamWithSnapshot(providerKey, model, messages, onDelta, null);
    }

    public void streamWithSnapshot(
        String providerKey,
        String model,
        List<Map<String, String>> messages,
        Consumer<String> onDelta,
        Map<String, Object> extraParams
    ) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(providerKey);
        try {
            cb.executeRunnable(() -> {
                User user = requireCurrentUser();
                LlmProvider provider = requireProvider(providerKey);
                String normalizedModel = normalizeModel(model, provider.defaultModel());
                LlmProviderConfig providerConfig = validateProviderSelection(provider.providerKey(), normalizedModel);
                String apiKey = resolveApiKey(user.getLlmApiKeyEncrypted(), provider);
                provider.streamChat(buildInvocation(providerConfig, normalizedModel, apiKey, user, messages, extraParams), onDelta);
            });
        } catch (Exception e) {
            log.warn("Stream call to LLM provider {} failed, attempting fallback routing", providerKey, e);
            if (e instanceof BusinessException && !(e instanceof LlmServerException || e instanceof LlmTimeoutException)) {
                throw (BusinessException) e;
            }
            executeFallbackStream(providerKey, model, messages, onDelta, extraParams);
        }
    }

    private String executeFallbackChat(String failedProviderKey, String model, List<Map<String, String>> messages, Map<String, Object> extraParams) {
        // openai-compatible 是用户 BYOK 配置，失败必须显式暴露，不得静默 fallback 到系统通道（否则会用系统 Key 调用户接口或泄露 Key）。
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(failedProviderKey)) {
            throw BusinessException.badRequest("自定义接口调用失败，请检查 Base URL、API Key 与模型后重试。");
        }
        List<LlmProviderConfig> configs = listFallbackProviderConfigs(failedProviderKey);

        if (configs.isEmpty()) {
            throw BusinessException.badRequest("主大模型不可用且无配置的可用备用通道");
        }

        LlmProviderConfig fallbackConfig = configs.get(0);
        String fallbackProviderKey = fallbackConfig.getProviderKey();
        log.info("Fallback routing triggered: {} -> {}", failedProviderKey, fallbackProviderKey);
        
        broadcastFallbackNotification(fallbackConfig.getDisplayName());

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(fallbackProviderKey);
        return cb.executeSupplier(() -> {
            User user = requireCurrentUser();
            LlmProvider provider = requireProvider(fallbackProviderKey);
            String fallbackModel = provider.defaultModel();
            // fallback 仅允许使用目标 provider 的系统 Key，绝不复用用户 BYOK Key，避免把用户 Key 发给其他 provider。
            String apiKey = resolveSystemApiKey(provider);
            return provider.chat(buildInvocation(fallbackConfig, fallbackModel, apiKey, user, messages, extraParams));
        });
    }

    private void executeFallbackStream(String failedProviderKey, String model, List<Map<String, String>> messages, Consumer<String> onDelta, Map<String, Object> extraParams) {
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(failedProviderKey)) {
            throw BusinessException.badRequest("自定义接口调用失败，请检查 Base URL、API Key 与模型后重试。");
        }
        List<LlmProviderConfig> configs = listFallbackProviderConfigs(failedProviderKey);

        if (configs.isEmpty()) {
            throw BusinessException.badRequest("主大模型不可用且无配置的可用备用通道");
        }

        LlmProviderConfig fallbackConfig = configs.get(0);
        String fallbackProviderKey = fallbackConfig.getProviderKey();
        log.info("Fallback routing triggered (stream): {} -> {}", failedProviderKey, fallbackProviderKey);
        
        broadcastFallbackNotification(fallbackConfig.getDisplayName());

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(fallbackProviderKey);
        cb.executeRunnable(() -> {
            User user = requireCurrentUser();
            LlmProvider provider = requireProvider(fallbackProviderKey);
            String fallbackModel = provider.defaultModel();
            String apiKey = resolveSystemApiKey(provider);
            provider.streamChat(buildInvocation(fallbackConfig, fallbackModel, apiKey, user, messages, extraParams), onDelta);
        });
    }

    private List<LlmProviderConfig> listFallbackProviderConfigs(String failedProviderKey) {
        // DB guard is the primary filter: the SQL .ne(..., openai-compatible) clause excludes
        // user-configured BYOK providers from the fallback list at the data source. The in-memory
        // filter below is a defensive belt-and-braces guard for mocked or custom mapper paths
        // where the SQL clause is not actually evaluated.
        return llmProviderConfigMapper.selectList(new LambdaQueryWrapper<LlmProviderConfig>()
            .eq(LlmProviderConfig::getEnabled, 1)
            .ne(LlmProviderConfig::getProviderKey, failedProviderKey)
            .ne(LlmProviderConfig::getProviderKey, OpenAiCompatibleProvider.PROVIDER_KEY)
            .orderByAsc(LlmProviderConfig::getId))
            .stream()
            .filter(config -> !OpenAiCompatibleProvider.PROVIDER_KEY.equals(config.getProviderKey()))
            .toList();
    }

    private void broadcastFallbackNotification(String displayName) {
        Long sessionId = UserContext.getCurrentSessionId();
        if (sessionId != null) {
            realtimePort.publish(sessionId, "fallback", "已为您自动切换至备用通道: " + displayName);
        }
    }

    public LlmProviderConfig validateProviderSelection(String providerKey, String model) {
        return selectionResolver.validateProviderSelection(providerKey, model);
    }

    private LlmProvider.LlmInvocation buildInvocation(
        LlmProviderConfig providerConfig,
        String model,
        String apiKey,
        User user,
        List<Map<String, String>> messages,
        Map<String, Object> callerExtraParams
    ) {
        Integer maxTokens = user.getLlmMaxTokens();
        String thinkingDepth = user.getLlmThinkingDepth();
        Map<String, Object> extraParams = null;
        if (thinkingDepth != null && !thinkingDepth.isBlank()) {
            extraParams = new HashMap<>();
            extraParams.put("thinking_depth", thinkingDepth);
        }
        if (callerExtraParams != null && !callerExtraParams.isEmpty()) {
            if (extraParams == null) {
                extraParams = new HashMap<>();
            }
            extraParams.putAll(callerExtraParams);
        }
        String baseUrl = providerConfig.getBaseUrl();
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(providerConfig.getProviderKey())) {
            baseUrl = OpenAiCompatibleUrl.toChatCompletionsUrl(user.getLlmBaseUrl());
        }
        return new LlmProvider.LlmInvocation(
            baseUrl,
            model,
            apiKey,
            messages,
            maxTokens,
            extraParams
        );
    }

    /**
     * 草稿测试专用：用显式 baseUrl/apiKey 构建 invocation，不读 user 表的 maxTokens/thinkingDepth/baseUrl。
     */
    private LlmProvider.LlmInvocation buildInvocationExplicit(
        LlmProviderConfig providerConfig,
        String model,
        String baseUrl,
        String apiKey,
        List<Map<String, String>> messages,
        Integer maxTokens,
        Map<String, Object> callerExtraParams
    ) {
        String resolvedBaseUrl = providerConfig.getBaseUrl();
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(providerConfig.getProviderKey())) {
            resolvedBaseUrl = OpenAiCompatibleUrl.toChatCompletionsUrl(baseUrl);
        }
        return new LlmProvider.LlmInvocation(
            resolvedBaseUrl,
            model,
            apiKey,
            messages,
            maxTokens,
            callerExtraParams
        );
    }

    private User requireCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user;
    }

    private LlmProvider requireProvider(String providerKey) {
        return providerRegistry.require(providerKey);
    }

    private LlmProviderConfig requireEnabledProviderConfig(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            throw BusinessException.badRequest("接入方式不能为空");
        }
        LlmProviderConfig providerConfig = llmProviderConfigMapper.selectOne(new LambdaQueryWrapper<LlmProviderConfig>()
            .eq(LlmProviderConfig::getProviderKey, providerKey)
            .eq(LlmProviderConfig::getEnabled, 1)
            .last("LIMIT 1"));
        if (providerConfig == null) {
            throw BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换接入方式");
        }
        return providerConfig;
    }

    private String normalizeModel(String model, String defaultModel) {
        return (model == null || model.isBlank()) ? defaultModel : model;
    }

    private String resolveApiKey(String encryptedUserKey, LlmProvider provider) {
        String userKey = decryptUserApiKey(encryptedUserKey);
        if (userKey != null && !userKey.isBlank()) {
            return userKey;
        }
        String systemApiKey = provider.systemApiKey();
        if (systemApiKey != null && !systemApiKey.isBlank()) {
            return systemApiKey;
        }
        throw BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换接入方式");
    }

    /**
     * fallback 专用：只允许使用目标 provider 的系统 Key，绝不解密用户 BYOK Key。
     */
    private String resolveSystemApiKey(LlmProvider provider) {
        String systemApiKey = provider.systemApiKey();
        if (systemApiKey != null && !systemApiKey.isBlank()) {
            return systemApiKey;
        }
        throw BusinessException.badRequest("备用通道未配置系统 Key，无法 fallback");
    }

    private String resolveExplicitApiKey(LlmProvider provider, String explicitApiKey) {
        if (explicitApiKey != null && !explicitApiKey.isBlank()) {
            return explicitApiKey;
        }
        if (OpenAiCompatibleProvider.PROVIDER_KEY.equals(provider.providerKey())) {
            throw BusinessException.badRequest("自定义接口 API Key 不能为空");
        }
        String systemApiKey = provider.systemApiKey();
        if (systemApiKey != null && !systemApiKey.isBlank()) {
            return systemApiKey;
        }
        throw BusinessException.badRequest("模型服务暂不可用，请稍后重试或切换接入方式");
    }

    private String decryptUserApiKey(String encryptedUserKey) {
        if (encryptedUserKey == null || encryptedUserKey.isBlank()) {
            return null;
        }
        try {
            return aesGcmEncryptor.decrypt(encryptedUserKey);
        } catch (BusinessException exception) {
            log.warn("Failed to decrypt user API key, fallback to system default");
            return null;
        }
    }

    private LlmProviderResponse toResponse(LlmProviderConfig providerConfig) {
        return new LlmProviderResponse(
            providerConfig.getProviderKey(),
            providerConfig.getDisplayName(),
            parseModels(providerConfig.getAvailableModels()),
            providerConfig.getEnabled()
        );
    }

    private List<String> parseModels(String availableModels) {
        if (availableModels == null || availableModels.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(availableModels, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest("接入方式配置格式错误");
        }
    }
}
