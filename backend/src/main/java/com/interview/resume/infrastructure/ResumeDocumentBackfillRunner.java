package com.interview.resume.infrastructure;

import com.interview.resume.application.BackfillResumeDocuments;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "prelude.resume.backfill-on-startup",
    havingValue = "true",
    matchIfMissing = true
)
public class ResumeDocumentBackfillRunner implements ApplicationRunner {

    private final BackfillResumeDocuments backfillResumeDocuments;
    @Qualifier("resumeBackfillExecutor")
    private final Executor resumeBackfillExecutor;

    @Override
    public void run(ApplicationArguments args) {
        resumeBackfillExecutor.execute(backfillResumeDocuments::execute);
    }
}
