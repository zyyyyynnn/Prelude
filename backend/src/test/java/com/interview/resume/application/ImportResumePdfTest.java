package com.interview.resume.application;

import com.interview.resume.application.port.PdfTextExtractor;
import com.interview.resume.application.port.ResumeParser;
import com.interview.resume.application.port.ResumeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportResumePdfTest {

    @Test
    void importsPdfThroughPortsAndCreatesCanonicalDocument() {
        PdfTextExtractor extractor = mock(PdfTextExtractor.class);
        ResumeParser parser = mock(ResumeParser.class);
        ResumeRepository repository = mock(ResumeRepository.class);
        byte[] pdf = "%PDF content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(extractor.extract(pdf)).thenReturn("完整 PDF 原文");
        when(parser.parse(42L, "完整 PDF 原文")).thenReturn(new ResumeParser.ParsedResume(
            List.of("Java"), List.of(new ResumeParser.ParsedProject("Prelude", "面试系统"))
        ));
        when(repository.create(any())).thenAnswer(invocation -> {
            ResumeRepository.NewResume draft = invocation.getArgument(0);
            return new ResumeRepository.StoredResume(
                9L, draft.userId(), draft.fileName(), draft.rawText(), draft.document(), 1,
                draft.sourceType(), LocalDateTime.now()
            );
        });

        ImportResumeResult result = new ImportResumePdf(extractor, parser, repository)
            .execute(42L, "resume.pdf", pdf);

        assertThat(result.resumeId()).isEqualTo(9L);
        assertThat(result.skills()).containsExactly("Java");
        assertThat(result.projects()).singleElement().satisfies(project ->
            assertThat(project.name()).isEqualTo("Prelude")
        );
        verify(repository).create(org.mockito.ArgumentMatchers.argThat(draft ->
            draft.rawText().equals("完整 PDF 原文")
                && draft.document().extras().contains("完整 PDF 原文")
                && draft.sourceType().equals("pdf_import")
        ));
    }
}
