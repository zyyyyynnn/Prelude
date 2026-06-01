package com.interview.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class InterviewReportParser {

    private static final int FALLBACK_SCORE = 6;

    private final ObjectMapper objectMapper;

    InterviewReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ParsedReport parse(String content) {
        String rawContent = content == null ? "" : content.trim();
        String jsonContent = stripJsonFence(rawContent);
        if (!jsonContent.startsWith("{")) {
            return fallback(rawContent);
        }
        try {
            StructuredReport report = objectMapper.readValue(jsonContent, StructuredReport.class);
            return new ParsedReport(
                fallbackText(report.reportMarkdown(), rawContent),
                clampScore(scoreValue(report.scores(), ScoreType.TECHNICAL)),
                clampScore(scoreValue(report.scores(), ScoreType.EXPRESSION)),
                clampScore(scoreValue(report.scores(), ScoreType.LOGIC))
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.error("Failed to parse structured interview report, fallback scores will be used: {}", exception.getMessage());
            return fallback(rawContent);
        }
    }

    private ParsedReport fallback(String rawContent) {
        return new ParsedReport(rawContent, FALLBACK_SCORE, FALLBACK_SCORE, FALLBACK_SCORE);
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private String fallbackText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private Integer scoreValue(StructuredScores scores, ScoreType type) {
        if (scores == null) {
            return FALLBACK_SCORE;
        }
        return switch (type) {
            case TECHNICAL -> scores.technical();
            case EXPRESSION -> scores.expression();
            case LOGIC -> scores.logic();
        };
    }

    private int clampScore(Integer value) {
        if (value == null) {
            return FALLBACK_SCORE;
        }
        return Math.max(1, Math.min(10, value));
    }

    private enum ScoreType {
        TECHNICAL,
        EXPRESSION,
        LOGIC
    }

    record ParsedReport(String reportMarkdown, int technicalScore, int expressionScore, int logicScore) {
    }

    private record StructuredReport(String reportMarkdown, StructuredScores scores) {
    }

    private record StructuredScores(Integer technical, Integer expression, Integer logic) {
    }
}
