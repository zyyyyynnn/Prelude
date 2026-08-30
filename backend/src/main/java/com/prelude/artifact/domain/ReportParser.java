package com.prelude.artifact.domain;

import com.prelude.artifact.domain.InterviewReportDraft;

public interface ReportParser {

    ParsedReport parse(String content);

    InterviewReportDraft parseDraft(String content);

    record ParsedReport(
        String reportMarkdown,
        int technicalScore,
        int expressionScore,
        int logicScore
    ) {
    }
}
