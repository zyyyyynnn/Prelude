package com.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.interview.mapper")
@SpringBootApplication
public class InterviewBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewBackendApplication.class, args);
    }
}
