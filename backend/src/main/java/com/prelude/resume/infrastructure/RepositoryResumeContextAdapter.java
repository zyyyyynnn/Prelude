package com.prelude.resume.infrastructure;

import com.prelude.BusinessException;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import com.prelude.resume.application.port.ResumeRepository;
import org.springframework.stereotype.Component;

@Component
public class RepositoryResumeContextAdapter implements ResumeContextPort {

    private final ResumeRepository repository;

    public RepositoryResumeContextAdapter(ResumeRepository repository) {
        this.repository = repository;
    }

    @Override
    public ResumeProjection requireOwnedProjection(Long accountId, Long resumeId) {
        ResumeRepository.StoredResume resume = repository.findById(resumeId)
            .orElseThrow(() -> BusinessException.badRequest("简历不存在或无权访问"));
        if (!accountId.equals(resume.accountId())) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }
        return new ResumeProjection(
            resume.id(),
            resume.accountId(),
            resume.fileName(),
            resume.rawText(),
            resume.parsedSkills(),
            resume.parsedProjects().stream()
                .map(RepositoryResumeContextAdapter::projectSummary)
                .filter(summary -> !summary.isBlank())
                .toList()
        );
    }

    private static String projectSummary(ResumeRepository.ParsedProject project) {
        String name = clean(project.name());
        String description = clean(project.description());
        if (name.isEmpty()) {
            return description;
        }
        return description.isEmpty() ? name : name + "：" + description;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
