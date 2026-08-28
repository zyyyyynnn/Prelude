package com.prelude.resume.application;

import com.prelude.documents.api.DocumentContent;
import com.prelude.documents.api.DocumentExtractor;
import com.prelude.resume.application.port.ResumeParser;
import com.prelude.resume.application.port.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportResumePdfTest {

    @Test
    void importsExtractedPdfForTheRequestedOwner() {
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        ResumeParser parser = mock(ResumeParser.class);
        ResumeRepository repository = mock(ResumeRepository.class);
        byte[] content = "%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(extractor.extract("candidate.pdf", "application/pdf", content))
            .thenReturn(new DocumentContent(DocumentContent.Kind.TEXT, "Java\nPrelude platform"));
        ResumeParser.ParsedProject project = new ResumeParser.ParsedProject("Prelude", "Interview platform");
        when(parser.parse(7L, "Java\nPrelude platform"))
            .thenReturn(new ResumeParser.ParsedResume(List.of("Java"), List.of(project)));
        when(repository.create(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ResumeRepository.NewResume draft = invocation.getArgument(0);
            return new ResumeRepository.StoredResume(
                41L,
                draft.userId(),
                draft.fileName(),
                draft.rawText(),
                draft.document(),
                1,
                draft.sourceType(),
                LocalDateTime.now()
            );
        });

        ImportResumeResult result = new ImportResumePdf(extractor, parser, repository)
            .execute(7L, "candidate.pdf", content);

        assertThat(result.resumeId()).isEqualTo(41L);
        assertThat(result.skills()).containsExactly("Java");
        ArgumentCaptor<ResumeRepository.NewResume> draft = ArgumentCaptor.forClass(ResumeRepository.NewResume.class);
        verify(repository).create(draft.capture());
        assertThat(draft.getValue().userId()).isEqualTo(7L);
        assertThat(draft.getValue().fileName()).isEqualTo("candidate.pdf");
        assertThat(draft.getValue().sourceType()).isEqualTo("pdf_import");
        assertThat(draft.getValue().document().projects()).hasSize(1);
    }
}
