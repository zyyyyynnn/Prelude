package com.prelude.llm;

import com.prelude.BusinessException;
import com.prelude.llm.api.ModelCapabilityResponse.ReasoningLevel;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Formal reasoning vocabulary: AUTO/LOW/MEDIUM/HIGH. Anything else is invalid;
 * providers map these levels onto their real parameters and unsupported levels
 * are rejected instead of being silently downgraded.
 */
@Component
public class ReasoningLevels {

    private static final Set<String> VALID = Set.of("AUTO", "LOW", "MEDIUM", "HIGH");

    public ReasoningLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return ReasoningLevel.AUTO;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID.contains(normalized)) {
            throw BusinessException.badRequest("思考深度仅支持 AUTO、LOW、MEDIUM、HIGH");
        }
        return ReasoningLevel.valueOf(normalized);
    }
}
