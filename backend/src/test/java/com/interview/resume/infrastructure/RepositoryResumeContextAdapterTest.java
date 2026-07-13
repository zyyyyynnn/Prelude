package com.interview.resume.infrastructure;

import com.interview.shared.api.BusinessException;
import com.interview.resume.api.port.ResumeProjection;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryResumeContextAdapterTest {

    private final ResumeRepository repository = mock(ResumeRepository.class);

    @Test
    void returnsDocumentProjectionForOwnedResume() {
        when(repository.findById(3L)).thenReturn(Optional.of(stored(
            42L,
            new ResumeDocument(
                1, "zh-CN", null, "结构化摘要",
                List.of(new ResumeDocument.Skill("Java", "expert")),
                List.of(), List.of(), List.of(), List.of()
            ),
            4
        )));

        ResumeProjection projection = adapter("document").requireOwnedProjection(42L, 3L);

        assertThat(projection.plainText()).contains("结构化摘要").contains("Java（expert）");
        assertThat(projection.skills()).containsExactly("Java");
        assertThat(projection.documentVersion()).isEqualTo(4);
    }

    @Test
    void fallsBackToRawTextWhenDocumentIsUnavailable() {
        when(repository.findById(3L)).thenReturn(Optional.of(stored(42L, null, 0)));

        ResumeProjection projection = adapter("document").requireOwnedProjection(42L, 3L);

        assertThat(projection.plainText()).isEqualTo("legacy raw text");
        assertThat(projection.documentVersion()).isZero();
    }

    @Test
    void rawSourceSwitchIgnoresDocument() {
        ResumeDocument document = new ResumeDocument(
            1, "zh-CN", null, "document", List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(repository.findById(3L)).thenReturn(Optional.of(stored(42L, document, 9)));

        ResumeProjection projection = adapter("raw").requireOwnedProjection(42L, 3L);

        assertThat(projection.plainText()).isEqualTo("legacy raw text");
        assertThat(projection.documentVersion()).isZero();
    }

    @Test
    void rejectsResumeOwnedByAnotherUser() {
        when(repository.findById(3L)).thenReturn(Optional.of(stored(99L, null, 0)));

        assertThatThrownBy(() -> adapter("document").requireOwnedProjection(42L, 3L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    private RepositoryResumeContextAdapter adapter(String source) {
        return new RepositoryResumeContextAdapter(repository, source);
    }

    private ResumeRepository.StoredResume stored(Long owner, ResumeDocument document, int version) {
        return new ResumeRepository.StoredResume(
            3L, owner, "resume.pdf", "legacy raw text", document, version, "pdf_import", LocalDateTime.now()
        );
    }
}
