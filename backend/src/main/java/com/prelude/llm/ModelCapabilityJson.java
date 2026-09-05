package com.prelude.llm;

import com.prelude.llm.api.ModelCapabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** JSON representation for model capability truth stored in profiles and snapshots. */
@Component
@RequiredArgsConstructor
class ModelCapabilityJson {

    private final ObjectMapper objectMapper;

    String write(ModelCapabilityResponse capability) {
        try {
            return objectMapper.writeValueAsString(capability);
        } catch (Exception exception) {
            throw new IllegalStateException("Model capability serialization failed", exception);
        }
    }

    ModelCapabilityResponse read(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Model capability is missing");
        }
        try {
            return objectMapper.readValue(json, ModelCapabilityResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Model capability JSON is invalid", exception);
        }
    }

    String writeList(List<ModelCapabilityResponse> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities == null ? List.of() : capabilities);
        } catch (Exception exception) {
            throw new IllegalStateException("Fallback capability serialization failed", exception);
        }
    }

    List<ModelCapabilityResponse> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(
                json, new TypeReference<List<ModelCapabilityResponse>>() {
                }));
        } catch (Exception exception) {
            throw new IllegalStateException("Fallback capability JSON is invalid", exception);
        }
    }
}
