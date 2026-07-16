package com.interview.resume.infrastructure;

import com.interview.resume.api.port.ResumeImprovementPort;
import com.interview.resume.application.ResumeImprovementContext;
import com.interview.resume.application.ResumeImprovementDraft;
import com.interview.resume.application.ResumeImprovementService;
import com.interview.resume.application.ResumeImprovementView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ResumeImprovementPortAdapter implements ResumeImprovementPort {

    private final ResumeImprovementService service;

    @Override
    public ImprovementContext requireContext(Long userId, Long resumeId) {
        ResumeImprovementContext context = service.requireContext(userId, resumeId);
        return new ImprovementContext(
            context.resumeId(),
            context.documentVersion(),
            context.statements().stream()
                .map(statement -> new EditableStatement(statement.targetPath(), statement.currentText()))
                .toList()
        );
    }

    @Override
    public List<StoredSuggestion> storeSuggestions(
        Long userId,
        Long resumeId,
        Long sessionId,
        List<SuggestionDraft> suggestions
    ) {
        List<ResumeImprovementDraft> drafts = suggestions == null
            ? List.of()
            : suggestions.stream()
                .map(suggestion -> new ResumeImprovementDraft(
                    suggestion.targetPath(), suggestion.currentText(), suggestion.proposedText(),
                    suggestion.rationale(), suggestion.evidence()
                ))
                .toList();
        return service.storeSuggestions(userId, resumeId, sessionId, drafts).stream()
            .map(this::toStored)
            .toList();
    }

    private StoredSuggestion toStored(ResumeImprovementView improvement) {
        return new StoredSuggestion(
            improvement.id(), improvement.resumeId(), improvement.sessionId(), improvement.targetPath(),
            improvement.currentText(), improvement.proposedText(), improvement.rationale(), improvement.evidence(),
            improvement.baseDocumentVersion(), improvement.status()
        );
    }
}
