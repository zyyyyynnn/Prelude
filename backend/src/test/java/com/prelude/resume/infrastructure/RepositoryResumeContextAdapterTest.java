package com.prelude.resume.infrastructure;

import com.prelude.BusinessException;
import com.prelude.resume.application.port.ResumeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryResumeContextAdapterTest {

    @Test
    void projectsStoredResumeResourceIntoInterviewContext() {
        ResumeRepository repository = mock(ResumeRepository.class);
        when(repository.findById(41L)).thenReturn(Optional.of(new ResumeRepository.StoredResume(
            41L,
            7L,
            "candidate.pdf",
            "Java 后端候选人原始简历",
            List.of("Java", "Spring Boot"),
            List.of(
                new ResumeRepository.ParsedProject("Prelude", "模拟面试平台"),
                new ResumeRepository.ParsedProject("基础设施", "")
            ),
            LocalDateTime.now()
        )));

        var projection = new RepositoryResumeContextAdapter(repository)
            .requireOwnedProjection(7L, 41L);

        assertThat(projection.resumeId()).isEqualTo(41L);
        assertThat(projection.ownerUserId()).isEqualTo(7L);
        assertThat(projection.displayName()).isEqualTo("candidate.pdf");
        assertThat(projection.plainText()).isEqualTo("Java 后端候选人原始简历");
        assertThat(projection.skills()).containsExactly("Java", "Spring Boot");
        assertThat(projection.projectsSummary())
            .containsExactly("Prelude：模拟面试平台", "基础设施");
    }

    @Test
    void rejectsResumeOwnedByAnotherUser() {
        ResumeRepository repository = mock(ResumeRepository.class);
        when(repository.findById(41L)).thenReturn(Optional.of(new ResumeRepository.StoredResume(
            41L,
            8L,
            "candidate.pdf",
            "raw",
            List.of(),
            List.of(),
            LocalDateTime.now()
        )));

        assertThatThrownBy(() -> new RepositoryResumeContextAdapter(repository)
            .requireOwnedProjection(7L, 41L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("简历不存在或无权访问");
    }
}
