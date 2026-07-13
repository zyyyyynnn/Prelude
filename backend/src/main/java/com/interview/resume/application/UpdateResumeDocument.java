package com.interview.resume.application;

import com.interview.shared.api.BusinessException;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateResumeDocument {

    private final ResumeRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public ResumeDocumentView execute(
        Long userId,
        Long resumeId,
        int expectedVersion,
        ResumeDocument document
    ) {
        ResumeRepository.StoredResume current = repository.findById(resumeId)
            .orElseThrow(() -> BusinessException.badRequest("简历不存在或无权访问"));
        if (!current.userId().equals(userId)) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }
        if (current.documentVersion() != expectedVersion) {
            throw BusinessException.badRequest("简历版本冲突，请刷新后重试");
        }
        if (document == null) {
            throw BusinessException.badRequest("简历文档不能为空");
        }
        if (!repository.updateDocument(resumeId, userId, expectedVersion, document, "editor")) {
            throw BusinessException.badRequest("简历版本冲突，请刷新后重试");
        }
        return new ResumeDocumentView(
            resumeId, current.fileName(), expectedVersion + 1, "editor", document
        );
    }
}
