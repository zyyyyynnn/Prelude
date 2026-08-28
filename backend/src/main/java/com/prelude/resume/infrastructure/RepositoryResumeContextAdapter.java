package com.prelude.resume.infrastructure;

import com.prelude.BusinessException;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.domain.ResumeDocumentProjection;
import com.prelude.resume.domain.ResumeDocumentProjector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RepositoryResumeContextAdapter implements ResumeContextPort {

    private final ResumeRepository repository;
    private final String projectionSource;
    private final ResumeDocumentProjector projector = new ResumeDocumentProjector();

    public RepositoryResumeContextAdapter(
        ResumeRepository repository,
        @Value("${prelude.resume.projection-source:document}") String projectionSource
    ) {
        this.repository = repository;
        this.projectionSource = projectionSource;
    }

    @Override
    public ResumeProjection requireOwnedProjection(Long userId, Long resumeId) {
        ResumeRepository.StoredResume resume = repository.findById(resumeId)
            .orElseThrow(() -> BusinessException.badRequest("简历不存在或无权访问"));
        if (!userId.equals(resume.userId())) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }
        if (!"raw".equalsIgnoreCase(projectionSource) && resume.document() != null) {
            ResumeDocumentProjection projection = projector.project(resume.document());
            return new ResumeProjection(
                resume.id(),
                resume.userId(),
                resume.fileName(),
                projection.plainText(),
                projection.skills(),
                projection.projectsSummary(),
                resume.documentVersion()
            );
        }
        return new ResumeProjection(
            resume.id(),
            resume.userId(),
            resume.fileName(),
            resume.rawText(),
            List.of(),
            List.of(),
            0
        );
    }
}
