package com.interview.resume.application;

import com.interview.shared.api.BusinessException;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.domain.ResumeDocumentProjector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateResumeDocument {

    private final ResumeRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public ResumeDocumentView execute(Long userId, String fileName, ResumeDocument document) {
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (fileName == null || fileName.isBlank()) {
            throw BusinessException.badRequest("简历名称不能为空");
        }
        if (document == null) {
            throw BusinessException.badRequest("简历文档不能为空");
        }
        String projection = new ResumeDocumentProjector().project(document).plainText();
        return ResumeDocumentView.from(repository.create(new ResumeRepository.NewResume(
            userId, fileName.trim(), projection, document, "editor"
        )));
    }
}
