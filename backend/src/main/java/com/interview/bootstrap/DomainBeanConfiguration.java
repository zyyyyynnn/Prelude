package com.interview.bootstrap;

import com.interview.insight.domain.InterviewReportAssembler;
import com.interview.interview.domain.InterviewStagePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
