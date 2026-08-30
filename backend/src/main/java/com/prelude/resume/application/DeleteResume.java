package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.resume.application.port.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteResume {

    private final ResumeRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long accountId, Long resumeId) {
        ResumeRepository.StoredResume resume = repository.findById(resumeId)
            .orElseThrow(() -> BusinessException.badRequest("简历不存在或无权访问"));
        if (!resume.accountId().equals(accountId)) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }
        if (repository.hasInterviewSessions(resumeId)) {
            throw BusinessException.badRequest("该简历已被面试使用，无法删除");
        }
        repository.delete(resumeId);
    }
}
