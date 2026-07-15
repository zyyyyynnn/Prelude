package com.interview.resume.application;

import com.interview.resume.application.port.ResumeImprovementRepository;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.domain.ResumeDocumentEditor;
import com.interview.resume.domain.ResumeImprovement;
import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResumeImprovementService {

    private static final int MAX_SUGGESTIONS = 3;

    private final ResumeRepository resumeRepository;
    private final ResumeImprovementRepository improvementRepository;
    private final ResumeDocumentEditor editor = new ResumeDocumentEditor();

    public ResumeImprovementContext requireContext(Long userId, Long resumeId) {
        ResumeRepository.StoredResume resume = requireOwnedResume(userId, resumeId);
        if (resume.document() == null) {
            throw BusinessException.badRequest("简历尚未完成结构化迁移");
        }
        List<ResumeImprovementContext.EditableStatement> statements = editor.statements(resume.document()).stream()
            .map(statement -> new ResumeImprovementContext.EditableStatement(
                statement.targetPath(), statement.currentText()
            ))
            .toList();
        return new ResumeImprovementContext(resume.id(), resume.documentVersion(), statements);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ResumeImprovementView> storeSuggestions(
        Long userId,
        Long resumeId,
        Long sessionId,
        List<ResumeImprovementDraft> suggestions
    ) {
        ResumeImprovementContext context = requireContext(userId, resumeId);
        List<ResumeImprovement> existing = improvementRepository.listBySession(sessionId);
        if (!existing.isEmpty()) {
            return existing.stream().map(ResumeImprovementView::from).toList();
        }

        Map<String, String> currentByPath = new LinkedHashMap<>();
        context.statements().forEach(statement -> currentByPath.put(statement.targetPath(), statement.currentText()));
        List<ResumeImprovementView> stored = new ArrayList<>();
        Set<String> usedPaths = new HashSet<>();
        int ordinal = 0;
        for (ResumeImprovementDraft draft : safe(suggestions)) {
            if (ordinal >= MAX_SUGGESTIONS || !valid(draft, currentByPath)) {
                continue;
            }
            String targetPath = draft.targetPath().trim();
            if (!usedPaths.add(targetPath)) {
                continue;
            }
            ResumeImprovement improvement = improvementRepository.insert(new ResumeImprovement(
                null,
                userId,
                resumeId,
                sessionId,
                ordinal,
                targetPath,
                draft.currentText().trim(),
                draft.proposedText().trim(),
                draft.rationale().trim(),
                draft.evidence().trim(),
                context.documentVersion(),
                ResumeImprovement.STATUS_PENDING,
                null,
                null,
                null
            ));
            stored.add(ResumeImprovementView.from(improvement));
            ordinal++;
        }
        return List.copyOf(stored);
    }

    public List<ResumeImprovementView> list(Long userId, Long resumeId, Long sessionId) {
        requireOwnedResume(userId, resumeId);
        List<ResumeImprovement> improvements = sessionId == null
            ? improvementRepository.listByResume(resumeId)
            : improvementRepository.listBySession(sessionId);
        return improvements.stream()
            .filter(item -> resumeId.equals(item.resumeId()) && userId.equals(item.userId()))
            .map(ResumeImprovementView::from)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeImprovementDecisionView accept(Long userId, Long improvementId) {
        ResumeImprovement improvement = requireOwnedImprovement(userId, improvementId);
        if (ResumeImprovement.STATUS_ACCEPTED.equals(improvement.status())) {
            ResumeRepository.StoredResume resume = requireOwnedResume(userId, improvement.resumeId());
            return new ResumeImprovementDecisionView(
                ResumeImprovementView.from(improvement), ResumeDocumentView.from(resume)
            );
        }
        requirePending(improvement);
        ResumeRepository.StoredResume resume = requireOwnedResume(userId, improvement.resumeId());
        ResumeDocument updated;
        try {
            updated = editor.apply(
                resume.document(), improvement.targetPath(), improvement.currentText(), improvement.proposedText()
            );
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
        if (!resumeRepository.updateDocument(
            resume.id(), userId, resume.documentVersion(), updated, "improvement"
        )) {
            throw BusinessException.badRequest("简历版本冲突，请刷新后重试");
        }
        int updatedVersion = resume.documentVersion() + 1;
        if (!improvementRepository.decide(
            improvement.id(), ResumeImprovement.STATUS_PENDING,
            ResumeImprovement.STATUS_ACCEPTED, updatedVersion
        )) {
            throw BusinessException.badRequest("建议状态已发生变化，请刷新后重试");
        }
        ResumeImprovement accepted = new ResumeImprovement(
            improvement.id(), improvement.userId(), improvement.resumeId(), improvement.sessionId(),
            improvement.ordinal(), improvement.targetPath(), improvement.currentText(), improvement.proposedText(),
            improvement.rationale(), improvement.evidence(), improvement.baseDocumentVersion(),
            ResumeImprovement.STATUS_ACCEPTED, updatedVersion, improvement.createdAt(), improvement.decidedAt()
        );
        ResumeDocumentView document = new ResumeDocumentView(
            resume.id(), resume.fileName(), updatedVersion, "improvement", updated
        );
        return new ResumeImprovementDecisionView(ResumeImprovementView.from(accepted), document);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeImprovementView reject(Long userId, Long improvementId) {
        ResumeImprovement improvement = requireOwnedImprovement(userId, improvementId);
        if (ResumeImprovement.STATUS_REJECTED.equals(improvement.status())) {
            return ResumeImprovementView.from(improvement);
        }
        requirePending(improvement);
        if (!improvementRepository.decide(
            improvement.id(), ResumeImprovement.STATUS_PENDING, ResumeImprovement.STATUS_REJECTED, null
        )) {
            throw BusinessException.badRequest("建议状态已发生变化，请刷新后重试");
        }
        return ResumeImprovementView.from(new ResumeImprovement(
            improvement.id(), improvement.userId(), improvement.resumeId(), improvement.sessionId(),
            improvement.ordinal(), improvement.targetPath(), improvement.currentText(), improvement.proposedText(),
            improvement.rationale(), improvement.evidence(), improvement.baseDocumentVersion(),
            ResumeImprovement.STATUS_REJECTED, null, improvement.createdAt(), improvement.decidedAt()
        ));
    }

    private ResumeRepository.StoredResume requireOwnedResume(Long userId, Long resumeId) {
        ResumeRepository.StoredResume resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> BusinessException.badRequest("简历不存在或无权访问"));
        if (!userId.equals(resume.userId())) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }
        return resume;
    }

    private ResumeImprovement requireOwnedImprovement(Long userId, Long improvementId) {
        ResumeImprovement improvement = improvementRepository.findById(improvementId)
            .orElseThrow(() -> BusinessException.badRequest("简历建议不存在或无权访问"));
        if (!userId.equals(improvement.userId())) {
            throw BusinessException.badRequest("简历建议不存在或无权访问");
        }
        return improvement;
    }

    private void requirePending(ResumeImprovement improvement) {
        if (!ResumeImprovement.STATUS_PENDING.equals(improvement.status())) {
            throw BusinessException.badRequest("该建议已处理");
        }
    }

    private boolean valid(ResumeImprovementDraft draft, Map<String, String> currentByPath) {
        if (draft == null || blank(draft.targetPath()) || blank(draft.proposedText())
            || blank(draft.rationale()) || blank(draft.evidence())) {
            return false;
        }
        String current = currentByPath.get(draft.targetPath().trim());
        return current != null
            && current.equals(text(draft.currentText()))
            && !current.equals(draft.proposedText().trim())
            && draft.proposedText().trim().length() <= 1_500
            && draft.rationale().trim().length() <= 1_000
            && draft.evidence().trim().length() <= 1_500;
    }

    private List<ResumeImprovementDraft> safe(List<ResumeImprovementDraft> values) {
        return values == null ? List.of() : values;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
