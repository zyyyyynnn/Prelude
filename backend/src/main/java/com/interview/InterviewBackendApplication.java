package com.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
    "com.interview.identity.infrastructure.persistence",
    "com.interview.catalog.infrastructure.persistence",
    "com.interview.resume.infrastructure.persistence",
    "com.interview.interview.infrastructure.persistence",
    "com.interview.insight.infrastructure.persistence",
    "com.interview.platform.llm.persistence",
    "com.interview.platform.retrieval.persistence",
    "com.interview.platform.job.infrastructure"
})
@EnableScheduling
@SpringBootApplication
public class InterviewBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewBackendApplication.class, args);
    }
}
