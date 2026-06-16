package com.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.ResumeUploadResponse;
import com.interview.entity.Resume;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.service.DevFixtureService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock private ResumeMapper resumeMapper;
    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private LlmRouter llmRouter;
    @Mock private DevFixtureService devFixtureService;

    private ResumeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResumeServiceImpl(
            resumeMapper,
            interviewSessionMapper,
            new ObjectMapper(),
            llmRouter,
            devFixtureService
        );
        UserContext.setCurrentUserId(42L);
        lenient().when(devFixtureService.isEnabled()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void uploadParsesPdfWithLlmAndStoresResume() throws Exception {
        when(llmRouter.chatCurrentUser(any())).thenReturn("""
            {"skills":["Java","Spring"],"projects":[{"name":"Prelude","description":"面试系统"}]}
            """);
        doAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(11L);
            return 1;
        }).when(resumeMapper).insert(any(Resume.class));

        ResumeUploadResponse response = service.upload(pdfFile("resume.pdf", "Java Spring project experience"));

        assertThat(response.getResumeId()).isEqualTo(11L);
        assertThat(response.getSkills()).contains("Java", "Spring");
        verify(resumeMapper).insert(any(Resume.class));
    }

    @Test
    void uploadRejectsInvalidPdfBytes() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "%PDFbad".getBytes()
        );

        assertThatThrownBy(() -> service.upload(file))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("PDF 文本提取失败");
    }

    @Test
    void uploadRejectsPdfWithoutExtractableText() throws Exception {
        assertThatThrownBy(() -> service.upload(emptyPdfFile("empty.pdf")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未提取到有效文本");
    }

    @Test
    void deleteRejectsResumeOwnedByAnotherUser() {
        Resume resume = new Resume();
        resume.setId(9L);
        resume.setUserId(99L);
        when(resumeMapper.selectById(9L)).thenReturn(resume);

        assertThatThrownBy(() -> service.deleteCurrentUserResume(9L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void listReturnsCurrentUserResumesWithSessionCount() {
        Resume resume = new Resume();
        resume.setId(9L);
        resume.setUserId(42L);
        resume.setFileName("resume.pdf");
        resume.setCreatedAt(LocalDateTime.now());
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));
        when(interviewSessionMapper.selectMaps(any())).thenReturn(List.of(Map.of("resumeId", 9L, "cnt", 2L)));

        var responses = service.listCurrentUserResumes();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).sessionCount()).isEqualTo(2L);
        assertThat(responses.get(0).inUse()).isTrue();
    }

    @Test
    void deleteOwnedUnusedResume() {
        Resume resume = new Resume();
        resume.setId(9L);
        resume.setUserId(42L);
        when(resumeMapper.selectById(9L)).thenReturn(resume);
        when(interviewSessionMapper.selectCount(any())).thenReturn(0L);

        service.deleteCurrentUserResume(9L);

        verify(resumeMapper).deleteById(9L);
    }

    private MockMultipartFile pdfFile(String filename, String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return new MockMultipartFile("file", filename, "application/pdf", out.toByteArray());
        }
    }

    private MockMultipartFile emptyPdfFile(String filename) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return new MockMultipartFile("file", filename, "application/pdf", out.toByteArray());
        }
    }
}
