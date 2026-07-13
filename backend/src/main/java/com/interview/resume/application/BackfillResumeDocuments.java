package com.interview.resume.application;

import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.domain.ResumeDocumentFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BackfillResumeDocuments {

    private final ResumeRepository repository;
    private final int batchSize;

    public BackfillResumeDocuments(
        ResumeRepository repository,
        @Value("${prelude.resume.backfill-batch-size:100}") int batchSize
    ) {
        this.repository = repository;
        this.batchSize = Math.max(1, batchSize);
    }

    public ResumeMigrationReport execute() {
        long total = repository.countWithoutDocument();
        long succeeded = 0;
        long failed = 0;
        long skipped = 0;
        long afterId = 0;
        while (true) {
            List<ResumeRepository.LegacyResume> batch = repository.findWithoutDocumentAfter(afterId, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (ResumeRepository.LegacyResume legacy : batch) {
                afterId = Math.max(afterId, legacy.id());
                try {
                    ResumeDocument document = ResumeDocumentFactory.fromImport(
                        legacy.rawText(),
                        legacy.skills(),
                        legacy.projects().stream()
                            .map(project -> new ResumeDocumentFactory.ImportedProject(
                                project.name(), project.description()
                            ))
                            .toList()
                    );
                    if (repository.backfillDocument(legacy.id(), document)) {
                        succeeded++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("Resume document backfill failed for resume {}: {}", legacy.id(), exception.getMessage());
                    log.debug("Resume document backfill failure", exception);
                }
            }
        }
        ResumeMigrationReport report = new ResumeMigrationReport(total, succeeded, failed, skipped);
        log.info(
            "Resume document backfill completed: total={}, succeeded={}, failed={}, skipped={}, successRate={}",
            report.total(), report.succeeded(), report.failed(), report.skipped(), report.successRate()
        );
        return report;
    }
}
