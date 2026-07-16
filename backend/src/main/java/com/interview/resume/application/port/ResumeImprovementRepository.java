package com.interview.resume.application.port;

import com.interview.resume.domain.ResumeImprovement;

import java.util.List;
import java.util.Optional;

public interface ResumeImprovementRepository {

    List<ResumeImprovement> listBySession(Long sessionId);

    List<ResumeImprovement> listByResume(Long resumeId);

    Optional<ResumeImprovement> findById(Long improvementId);

    ResumeImprovement insert(ResumeImprovement improvement);

    boolean decide(Long improvementId, String expectedStatus, String status, Integer appliedDocumentVersion);
}
