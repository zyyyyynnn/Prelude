package com.interview.resume.application;

import com.interview.shared.api.BusinessException;
import com.interview.resume.application.port.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetResumeDocument {

    private final ResumeRepository repository;

    public ResumeDocumentView execute(Long userId, Long resumeId) {
        ResumeRepository.StoredResume resume = requireOwned(userId, resumeId);
        if (resume.document() == null) {
            throw BusinessException.badRequest("简历结构化文档尚未完成迁移");
        }
        return ResumeDocumentView.from(resume);
    }

    private ResumeRepository.StoredResume requireOwned(Long userId, Long resumeId) {
        ResumeRepository.StoredResume resume = repository.findById(resumeId)
            .orElseThrow(() -> BusinessException.badRequest("简历不存在或无权访问"));
        if (!resume.userId().equals(userId)) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }
        return resume;
    }
}
