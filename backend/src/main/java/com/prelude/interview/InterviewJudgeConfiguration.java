package com.prelude.interview;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InterviewJudgeRetryProperties.class)
class InterviewJudgeConfiguration {
}
