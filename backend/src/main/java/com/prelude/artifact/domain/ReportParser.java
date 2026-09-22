package com.prelude.artifact.domain;

import com.prelude.artifact.domain.InterviewReportDraft;

import java.util.List;

/**
 * Reads model output into this module's types. One implementation owns the JSON reading,
 * so no use case unwraps a fence or builds an ObjectMapper of its own.
 */
public interface ReportParser {

    ParsedReport parse(String content);

    InterviewReportDraft parseDraft(String content);

    /** A JSON array of items, for the extractions that are not the report itself. */
    <T> List<T> parseItems(String content, Class<T> itemType);

    record ParsedReport(
        String reportMarkdown,
        int technicalScore,
        int expressionScore,
        int logicScore
    ) {
    }
}
