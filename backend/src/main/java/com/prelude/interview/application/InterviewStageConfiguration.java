package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewStagePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterviewStageConfiguration {

    @Bean
    InterviewStagePolicy interviewStagePolicy() {
        return new InterviewStagePolicy();
    }
}
