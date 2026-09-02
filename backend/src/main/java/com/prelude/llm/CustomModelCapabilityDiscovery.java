package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Selected-model capability discovery for user-supplied protocols. Anthropic
 * exposes model metadata directly; OpenAI-style protocols require a narrow
 * parameter-acceptance probe. Any inconclusive probe stays conservative.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomModelCapabilityDiscovery {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final List<ReasoningLevel> PROBE_LEVELS = List.of(
        ReasoningLevel.LOW, ReasoningLevel.MEDIUM, ReasoningLevel.HIGH);

    private final ModelCapabilityCatalog capabilityCatalog;
    private final CustomLlmEgressPolicy egressPolicy;
    private final EgressHttpClientFactory egressHttpClientFactory;
    private final ObjectMapper objectMapper;

    public ModelCapabilityResponse discover(String provider, String baseUrl, String apiKey, String model) {
        CustomLlmProtocol protocol = CustomLlmProtocol.require(provider);
        return switch (protocol) {
            case ANTHROPIC_MESSAGES -> discoverAnthropic(provider, baseUrl, apiKey, model);
            case OPENAI_RESPONSES, OPENAI_CHAT_COMPLETIONS -> probeOpenAi(protocol, provider, baseUrl, apiKey, model);
        };
    }

    private ModelCapabilityResponse discoverAnthropic(
        String provider,
        String baseUrl,
        String apiKey,
        String model
    ) {
        try {
            okhttp3.HttpUrl url = okhttp3.HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment("models")
                .addPathSegment(model)
                .build();
            egressPolicy.validateUrl(url);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .get()
                .build();
            try (Response response = egressHttpClientFactory.discoveryClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return conservative(provider, model);
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode capabilities = root.path("capabilities");
                JsonNode effort = capabilities.path("effort");
                JsonNode thinking = capabilities.path("thinking");
                boolean adaptiveThinking = thinking.path("supported").asBoolean(false)
                    && thinking.path("types").path("adaptive").path("supported").asBoolean(false);
                if (!effort.path("supported").asBoolean(false) || !adaptiveThinking) {
                    return conservative(provider, model);
                }
                List<ReasoningLevel> levels = new ArrayList<>();
                levels.add(ReasoningLevel.AUTO);
                addIfSupported(levels, effort, "low", ReasoningLevel.LOW);
                addIfSupported(levels, effort, "medium", ReasoningLevel.MEDIUM);
                addIfSupported(levels, effort, "high", ReasoningLevel.HIGH);
                return capabilityCatalog.customCapability(provider, model, levels);
            }
        } catch (Exception exception) {
            log.info("Anthropic capability discovery was inconclusive for model {}; using AUTO only", model);
            return conservative(provider, model);
        }
    }

    private ModelCapabilityResponse probeOpenAi(
        CustomLlmProtocol protocol,
        String provider,
        String baseUrl,
        String apiKey,
        String model
    ) {
        if (probeOpenAiEffort(protocol, baseUrl, apiKey, model, "prelude_probe_invalid")
            != ProbeResult.UNSUPPORTED) {
            log.info("Custom OpenAI endpoint did not prove reasoning parameter recognition for model {}; using AUTO only",
                model);
            return conservative(provider, model);
        }
        List<ReasoningLevel> supported = new ArrayList<>();
        supported.add(ReasoningLevel.AUTO);
        for (ReasoningLevel level : PROBE_LEVELS) {
            ProbeResult result = probeOpenAiEffort(
                protocol, baseUrl, apiKey, model, level.name().toLowerCase(java.util.Locale.ROOT));
            if (result == ProbeResult.INCONCLUSIVE) {
                log.info("Custom OpenAI reasoning probe was inconclusive for model {}; using AUTO only", model);
                return conservative(provider, model);
            }
            if (result == ProbeResult.SUPPORTED) {
                supported.add(level);
            }
        }
        return capabilityCatalog.customCapability(provider, model, supported);
    }

    private ProbeResult probeOpenAiEffort(
        CustomLlmProtocol protocol,
        String baseUrl,
        String apiKey,
        String model,
        String effort
    ) {
        String endpoint = baseUrl + protocol.endpointSuffix();
        try {
            egressPolicy.validateConfiguredEndpoint(endpoint);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            if (protocol == CustomLlmProtocol.OPENAI_RESPONSES) {
                payload.put("input", "Reply OK.");
                payload.put("reasoning", Map.of("effort", effort));
            } else {
                payload.put("messages", List.of(Map.of("role", "user", "content", "Reply OK.")));
                payload.put("reasoning_effort", effort);
            }
            Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                .build();
            try (Response response = egressHttpClientFactory.discoveryClient().newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return ProbeResult.SUPPORTED;
                }
                if (response.code() == 400 || response.code() == 422) {
                    return ProbeResult.UNSUPPORTED;
                }
                return ProbeResult.INCONCLUSIVE;
            }
        } catch (IOException exception) {
            return ProbeResult.INCONCLUSIVE;
        } catch (RuntimeException exception) {
            return ProbeResult.INCONCLUSIVE;
        }
    }

    private void addIfSupported(
        List<ReasoningLevel> levels,
        JsonNode effort,
        String field,
        ReasoningLevel level
    ) {
        if (effort.path(field).path("supported").asBoolean(false)) {
            levels.add(level);
        }
    }

    private ModelCapabilityResponse conservative(String provider, String model) {
        return capabilityCatalog.customCapability(provider, model, List.of(ReasoningLevel.AUTO));
    }

    private enum ProbeResult {
        SUPPORTED,
        UNSUPPORTED,
        INCONCLUSIVE
    }
}
