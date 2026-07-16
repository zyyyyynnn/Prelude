package com.interview.resume.application;

import com.interview.resume.application.port.ResumeImprovementRepository;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.domain.ResumeImprovement;
import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeImprovementServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeImprovementRepository improvementRepository;

    private ResumeImprovementService service;

    @BeforeEach
    void setUp() {
        service = new ResumeImprovementService(resumeRepository, improvementRepository);
    }

    @Test
    void storesOnlySuggestionsMatchingCurrentWhitelistedFields() {
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(resume(2)));
        when(improvementRepository.listBySession(7L)).thenReturn(List.of());
        when(improvementRepository.insert(any())).thenAnswer(invocation -> {
            ResumeImprovement value = invocation.getArgument(0);
            return copy(value, 11L, value.status(), null);
        });

        var stored = service.storeSuggestions(42L, 5L, 7L, List.of(
            new ResumeImprovementDraft(
                "projects[0].bullets[0]", "负责接口开发", "将接口 P95 降至 180ms", "补充量化结果", "候选人回答"
            ),
            new ResumeImprovementDraft("profile.email", "a@example.com", "x@example.com", "无关字段", "候选人回答")
        ));

        assertThat(stored).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(11L);
            assertThat(item.baseDocumentVersion()).isEqualTo(2);
        });
    }

    @Test
    void acceptsSuggestionWithDocumentCasAndRecordsAppliedVersion() {
        ResumeImprovement improvement = improvement(ResumeImprovement.STATUS_PENDING);
        when(improvementRepository.findById(11L)).thenReturn(Optional.of(improvement));
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(resume(2)));
        when(resumeRepository.updateDocument(any(), any(), any(Integer.class), any(), any())).thenReturn(true);
        when(improvementRepository.decide(11L, "pending", "accepted", 3)).thenReturn(true);

        ResumeImprovementDecisionView result = service.accept(42L, 11L);

        assertThat(result.improvement().status()).isEqualTo("accepted");
        assertThat(result.resume().documentVersion()).isEqualTo(3);
        assertThat(result.resume().document().projects().getFirst().bullets())
            .containsExactly("将接口 P95 降至 180ms");
        verify(resumeRepository).updateDocument(5L, 42L, 2, result.resume().document(), "improvement");
    }

    @Test
    void doesNotOverwriteWhenFieldChangedSinceSuggestionWasCreated() {
        when(improvementRepository.findById(11L)).thenReturn(Optional.of(improvement("pending")));
        ResumeRepository.StoredResume changed = resume(3);
        ResumeDocument document = changed.document();
        ResumeDocument changedDocument = new ResumeDocument(
            document.schemaVersion(), document.locale(), document.profile(), document.summary(), document.skills(),
            document.experiences(), List.of(new ResumeDocument.Project(
                "Prelude", "后端开发", List.of("Spring Boot"), List.of("用户已手动修改"), ""
            )), document.education(), document.extras()
        );
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(new ResumeRepository.StoredResume(
            5L, 42L, "resume.pdf", "", changedDocument, 3, "editor", LocalDateTime.now()
        )));

        assertThatThrownBy(() -> service.accept(42L, 11L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("发生变化");
    }

    private ResumeRepository.StoredResume resume(int version) {
        ResumeDocument document = new ResumeDocument(
            1, "zh-CN", null, "后端工程师", List.of(), List.of(),
            List.of(new ResumeDocument.Project(
                "Prelude", "后端开发", List.of("Spring Boot"), List.of("负责接口开发"), ""
            )), List.of(), List.of()
        );
        return new ResumeRepository.StoredResume(
            5L, 42L, "resume.pdf", "", document, version, "editor", LocalDateTime.now()
        );
    }

    private ResumeImprovement improvement(String status) {
        return new ResumeImprovement(
            11L, 42L, 5L, 7L, 0, "projects[0].bullets[0]", "负责接口开发",
            "将接口 P95 降至 180ms", "补充量化结果", "候选人回答", 2, status,
            null, LocalDateTime.now(), null
        );
    }

    private ResumeImprovement copy(
        ResumeImprovement value,
        Long id,
        String status,
        Integer appliedVersion
    ) {
        return new ResumeImprovement(
            id, value.userId(), value.resumeId(), value.sessionId(), value.ordinal(), value.targetPath(),
            value.currentText(), value.proposedText(), value.rationale(), value.evidence(),
            value.baseDocumentVersion(), status, appliedVersion, LocalDateTime.now(), null
        );
    }
}
