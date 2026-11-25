package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.resume.application.port.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteResumeTest {

    private final ResumeRepository repository = mock(ResumeRepository.class);
    private final DeleteResume deleteResume = new DeleteResume(repository);

    @BeforeEach
    void setUp() {
        when(repository.findById(42L)).thenReturn(Optional.of(new ResumeRepository.StoredResume(
            42L, 7L, "resume.pdf", "text", List.of("Java"), List.of(), LocalDateTime.now())));
    }

    @Test
    void deletingAnUnusedResumeRemovesItsDatabaseRecord() {
        deleteResume.execute(7L, 42L);

        verify(repository).delete(42L);
    }

    @Test
    void resumeInUseCannotBeDeleted() {
        when(repository.hasInterviewSessions(42L)).thenReturn(true);

        assertThatThrownBy(() -> deleteResume.execute(7L, 42L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已被面试使用");

        verify(repository, never()).delete(42L);
    }

    @Test
    void anotherAccountCannotDeleteTheResume() {
        assertThatThrownBy(() -> deleteResume.execute(8L, 42L))
            .isInstanceOf(BusinessException.class);

        verify(repository, never()).delete(42L);
    }
}
