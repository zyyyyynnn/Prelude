package com.interview.resume.infrastructure;

import com.interview.resume.application.BackfillResumeDocuments;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResumeDocumentBackfillRunnerTest {

    @Test
    void runsConfiguredBackfillUseCaseAtStartup() throws Exception {
        BackfillResumeDocuments backfill = mock(BackfillResumeDocuments.class);

        new ResumeDocumentBackfillRunner(backfill).run(null);

        verify(backfill).execute();
    }
}
