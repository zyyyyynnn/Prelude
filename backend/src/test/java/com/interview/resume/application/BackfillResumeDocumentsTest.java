package com.interview.resume.application;

import com.interview.resume.application.port.ResumeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackfillResumeDocumentsTest {

    @Test
    void continuesAfterRowFailureAndReportsMigrationSuccessRate() {
        ResumeRepository repository = mock(ResumeRepository.class);
        when(repository.countWithoutDocument()).thenReturn(3L);
        when(repository.findWithoutDocumentAfter(0L, 100)).thenReturn(List.of(
            legacy(1L), legacy(2L), legacy(3L)
        ));
        when(repository.findWithoutDocumentAfter(3L, 100)).thenReturn(List.of());
        when(repository.backfillDocument(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(true);
        when(repository.backfillDocument(org.mockito.ArgumentMatchers.eq(2L), any()))
            .thenThrow(new IllegalStateException("write failed"));
        when(repository.backfillDocument(org.mockito.ArgumentMatchers.eq(3L), any())).thenReturn(false);

        ResumeMigrationReport report = new BackfillResumeDocuments(repository, 100).execute();

        assertThat(report.total()).isEqualTo(3);
        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.successRate()).isEqualTo(2.0 / 3.0);
    }

    @Test
    void emptyMigrationIsFullySuccessful() {
        ResumeRepository repository = mock(ResumeRepository.class);
        when(repository.countWithoutDocument()).thenReturn(0L);

        ResumeMigrationReport report = new BackfillResumeDocuments(repository, 100).execute();

        assertThat(report.successRate()).isEqualTo(1.0);
    }

    private ResumeRepository.LegacyResume legacy(Long id) {
        return new ResumeRepository.LegacyResume(
            id,
            "raw-" + id,
            List.of("Java"),
            List.of(new ResumeRepository.LegacyProject("Prelude", "面试系统"))
        );
    }
}
