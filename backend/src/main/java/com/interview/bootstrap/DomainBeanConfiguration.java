package com.interview.bootstrap;

import com.interview.insight.domain.InterviewReportAssembler;
import com.interview.interview.domain.InterviewStagePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainBeanConfiguration {

    @Bean
    InterviewReportAssembler interviewReportAssembler() {
        return new InterviewReportAssembler();
    }

    @Bean
    InterviewStagePolicy interviewStagePolicy() {
        return new InterviewStagePolicy();
    }
}
