package com.prelude.artifact.application;

import com.prelude.artifact.domain.InterviewReportAssembler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterviewReportConfiguration {

    @Bean
    InterviewReportAssembler interviewReportAssembler() {
        return new InterviewReportAssembler();
    }
}
