package com.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewReportParserTest {

    private final InterviewReportParser parser = new InterviewReportParser(new ObjectMapper());

    @Test
    void parsesStructuredReportJson() {
        InterviewReportParser.ParsedReport report = parser.parse("""
            {
              "reportMarkdown": "# 面试评估报告",
              "scores": {
                "technical": 9,
                "expression": 7,
                "logic": 8
              }
            }
            """);

        assertThat(report.reportMarkdown()).isEqualTo("# 面试评估报告");
        assertThat(report.technicalScore()).isEqualTo(9);
        assertThat(report.expressionScore()).isEqualTo(7);
        assertThat(report.logicScore()).isEqualTo(8);
    }

    @Test
    void fallsBackToSafeScoresAndRawMarkdownWhenJsonCannotBeParsed() {
        InterviewReportParser.ParsedReport report = parser.parse("# 非结构化报告");

        assertThat(report.reportMarkdown()).isEqualTo("# 非结构化报告");
        assertThat(report.technicalScore()).isEqualTo(6);
        assertThat(report.expressionScore()).isEqualTo(6);
        assertThat(report.logicScore()).isEqualTo(6);
    }

    @Test
    void clampsScoresToOneToTen() {
        InterviewReportParser.ParsedReport report = parser.parse("""
            {
              "reportMarkdown": "# 面试评估报告",
              "scores": {
                "technical": 15,
                "expression": 0,
                "logic": 8
              }
            }
            """);

        assertThat(report.technicalScore()).isEqualTo(10);
        assertThat(report.expressionScore()).isEqualTo(1);
        assertThat(report.logicScore()).isEqualTo(8);
    }
}
