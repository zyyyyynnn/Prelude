package com.interview.resume.infrastructure;

import com.interview.resume.application.BackfillResumeDocuments;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResumeDocumentBackfillRunnerTest {

    @Test
    void schedulesBackfillOnBackgroundExecutorWithoutBlockingStartupThread() throws Exception {
        BackfillResumeDocuments backfill = mock(BackfillResumeDocuments.class);
        Executor backfillExecutor = mock(Executor.class);

        new ResumeDocumentBackfillRunner(backfill, backfillExecutor).run(null);

        verify(backfillExecutor).execute(any());
        verify(backfill, never()).execute();
    }
}
