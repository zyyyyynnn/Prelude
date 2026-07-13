package com.interview.resume.application;

import com.interview.shared.api.BusinessException;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeDocumentCrudTest {

    private final ResumeRepository repository = mock(ResumeRepository.class);
    private final ResumeDocument document = new ResumeDocument(
        1, "zh-CN", null, "摘要", List.of(), List.of(), List.of(), List.of(), List.of()
    );

    @Test
    void createsEditorDocument() {
        when(repository.create(org.mockito.ArgumentMatchers.any())).thenReturn(stored(8L, 42L, 1, document));

        ResumeDocumentView view = new CreateResumeDocument(repository).execute(42L, "我的简历", document);

        assertThat(view.resumeId()).isEqualTo(8L);
        assertThat(view.documentVersion()).isEqualTo(1);
        assertThat(view.document()).isEqualTo(document);
    }

    @Test
    void getsOnlyOwnedDocument() {
        when(repository.findById(8L)).thenReturn(Optional.of(stored(8L, 99L, 1, document)));

        assertThatThrownBy(() -> new GetResumeDocument(repository).execute(42L, 8L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void updatesUsingOptimisticDocumentVersion() {
        when(repository.findById(8L)).thenReturn(Optional.of(stored(8L, 42L, 2, document)));
        when(repository.updateDocument(8L, 42L, 2, document, "editor")).thenReturn(true);

        ResumeDocumentView view = new UpdateResumeDocument(repository).execute(42L, 8L, 2, document);

        assertThat(view.documentVersion()).isEqualTo(3);
    }

    @Test
    void rejectsStaleUpdate() {
        when(repository.findById(8L)).thenReturn(Optional.of(stored(8L, 42L, 3, document)));

        assertThatThrownBy(() -> new UpdateResumeDocument(repository).execute(42L, 8L, 2, document))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("版本冲突");
    }

    @Test
    void listsOwnedResumes() {
        ResumeRepository.ResumeListItem item = new ResumeRepository.ResumeListItem(
            8L, "我的简历", LocalDateTime.of(2026, 7, 12, 10, 0), 2L
        );
        when(repository.listByOwner(42L)).thenReturn(List.of(item));

        assertThat(new ListResumes(repository).execute(42L)).containsExactly(item);
    }

    @Test
    void deletesOwnedUnusedResume() {
        when(repository.findById(8L)).thenReturn(Optional.of(stored(8L, 42L, 1, document)));
        when(repository.hasInterviewSessions(8L)).thenReturn(false);

        new DeleteResume(repository).execute(42L, 8L);

        verify(repository).delete(8L);
    }

    private ResumeRepository.StoredResume stored(Long id, Long owner, int version, ResumeDocument value) {
        return new ResumeRepository.StoredResume(
            id, owner, "我的简历", "legacy", value, version, "editor", LocalDateTime.now()
        );
    }
}
