package com.interview.resume.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.resume.infrastructure.persistence.Resume;
import com.interview.resume.infrastructure.persistence.ResumeMapper;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.application.port.ResumeUsagePort;
import com.interview.resume.domain.ResumeDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisResumeRepositoryTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeUsagePort resumeUsagePort = mock(ResumeUsagePort.class);
    private final MybatisResumeRepository repository = new MybatisResumeRepository(
        resumeMapper, resumeUsagePort, new ObjectMapper()
    );

    @Test
    void createAtomicallyWritesDocumentProjectionAndLegacyFields() throws Exception {
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(17L);
            return 1;
        });
        ResumeDocument document = document("Java", "Prelude", "模块化单体");

        ResumeRepository.StoredResume stored = repository.create(new ResumeRepository.NewResume(
            42L, "resume.pdf", "完整 PDF 原文", document, "pdf_import"
        ));

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper).insert(captor.capture());
        Resume row = captor.getValue();
        assertThat(stored.id()).isEqualTo(17L);
        assertThat(row.getRawText()).isEqualTo("完整 PDF 原文");
        assertThat(row.getParsedSkills()).isEqualTo("[\"Java\"]");
        assertThat(row.getParsedProjects()).contains("Prelude").contains("模块化单体");
        assertThat(new ObjectMapper().readTree(row.getDocumentJson()).path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(row.getDocumentVersion()).isEqualTo(1);
        assertThat(row.getSourceType()).isEqualTo("pdf_import");
        assertThat(row.getPlainTextProjection()).contains("Java（expert）").contains("Prelude");
    }

    @Test
    void updateUsesExpectedVersionAndDualWritesLegacyProjection() {
        when(resumeMapper.update(any(), any())).thenReturn(1);

        boolean updated = repository.updateDocument(
            17L, 42L, 3, document("Go", "Gateway", "完成迁移"), "editor"
        );

        ArgumentCaptor<Resume> rowCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper).update(rowCaptor.capture(), any());
        Resume row = rowCaptor.getValue();
        assertThat(updated).isTrue();
        assertThat(row.getDocumentVersion()).isEqualTo(4);
        assertThat(row.getRawText()).isEqualTo(row.getPlainTextProjection());
        assertThat(row.getParsedSkills()).isEqualTo("[\"Go\"]");
        assertThat(row.getSourceType()).isEqualTo("editor");
    }

    @Test
    void invalidDocumentJsonRemainsReadableAsLegacyResume() {
        Resume row = new Resume();
        row.setId(17L);
        row.setUserId(42L);
        row.setFileName("resume.pdf");
        row.setRawText("legacy raw text");
        row.setDocumentJson("not-json");
        when(resumeMapper.selectById(17L)).thenReturn(row);

        Optional<ResumeRepository.StoredResume> stored = repository.findById(17L);

        assertThat(stored).isPresent();
        assertThat(stored.orElseThrow().document()).isNull();
        assertThat(stored.orElseThrow().rawText()).isEqualTo("legacy raw text");
    }

    private ResumeDocument document(String skill, String project, String bullet) {
        return new ResumeDocument(
            1, "zh-CN", null, "", List.of(new ResumeDocument.Skill(skill, "expert")), List.of(),
            List.of(new ResumeDocument.Project(project, "", List.of(), List.of(bullet), "")),
            List.of(), List.of()
        );
    }
}
