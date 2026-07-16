package com.interview.resume.api;

import com.interview.shared.web.GlobalExceptionHandler;
import com.interview.shared.web.JwtInterceptor;
import com.interview.resume.application.CreateResumeDocument;
import com.interview.resume.application.DeleteResume;
import com.interview.resume.application.GetResumeDocument;
import com.interview.resume.application.ImportResumePdf;
import com.interview.resume.application.ImportResumeResult;
import com.interview.resume.application.ListResumes;
import com.interview.resume.application.ResumeDocumentView;
import com.interview.resume.application.UpdateResumeDocument;
import com.interview.resume.application.ResumeImprovementService;
import com.interview.resume.application.ResumeImprovementDecisionView;
import com.interview.resume.application.ResumeImprovementView;
import com.interview.resume.application.port.ResumeParser;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.application.port.ResumeFixturePort;
import com.interview.platform.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResumeControllerWebMvcTest {

    @Mock private ImportResumePdf importResumePdf;
    @Mock private CreateResumeDocument createResumeDocument;
    @Mock private GetResumeDocument getResumeDocument;
    @Mock private UpdateResumeDocument updateResumeDocument;
    @Mock private ListResumes listResumes;
    @Mock private DeleteResume deleteResume;
    @Mock private ResumeImprovementService resumeImprovementService;
    @Mock private ResumeFixturePort devFixtureService;
    @Mock private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private final ResumeDocument document = new ResumeDocument(
        1, "zh-CN", null, "结构化摘要", List.of(), List.of(), List.of(), List.of(), List.of()
    );

    @BeforeEach
    void setUp() {
        lenient().when(jwtUtil.parseUserId("token")).thenReturn(42L);
        lenient().when(devFixtureService.isEnabled()).thenReturn(false);
        mockMvc = MockMvcBuilders.standaloneSetup(new ResumeController(
                importResumePdf,
                createResumeDocument,
                getResumeDocument,
                updateResumeDocument,
                listResumes,
                deleteResume,
                resumeImprovementService,
                devFixtureService
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addInterceptors(new JwtInterceptor(jwtUtil))
            .build();
    }

    @Test
    void uploadPreservesExistingContractThroughImportUseCase() throws Exception {
        when(importResumePdf.execute(any(), any(), any())).thenReturn(new ImportResumeResult(
            9L, List.of("Java"), List.of(new ResumeParser.ParsedProject("Prelude", "面试系统"))
        ));
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "%PDF test".getBytes()
        );

        mockMvc.perform(multipart("/api/resume/upload").file(file)
                .header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resumeId").value(9))
            .andExpect(jsonPath("$.data.skills[0]").value("Java"))
            .andExpect(jsonPath("$.data.projects[0].name").value("Prelude"));
    }

    @Test
    void createsGetsAndUpdatesStructuredDocument() throws Exception {
        when(createResumeDocument.execute(any(), any(), any())).thenReturn(view(9L, 1));
        when(getResumeDocument.execute(42L, 9L)).thenReturn(view(9L, 1));
        when(updateResumeDocument.execute(any(), any(), any(Integer.class), any())).thenReturn(view(9L, 2));
        String createBody = """
            {"fileName":"我的简历","document":{"schemaVersion":1,"locale":"zh-CN","summary":"结构化摘要","skills":[],"experiences":[],"projects":[],"education":[],"extras":[]}}
            """;
        String updateBody = """
            {"expectedVersion":1,"document":{"schemaVersion":1,"locale":"zh-CN","summary":"结构化摘要","skills":[],"experiences":[],"projects":[],"education":[],"extras":[]}}
            """;

        mockMvc.perform(post("/api/resume/document")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.documentVersion").value(1));
        mockMvc.perform(get("/api/resume/9/document").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.document.summary").value("结构化摘要"));
        mockMvc.perform(put("/api/resume/9/document")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.documentVersion").value(2));

        verify(updateResumeDocument).execute(42L, 9L, 1, document);
    }

    @Test
    void listsAndDecidesEvidenceBackedImprovements() throws Exception {
        ResumeImprovementView improvement = new ResumeImprovementView(
            11L,
            9L,
            7L,
            "projects[0].bullets[0]",
            "负责接口开发",
            "将接口 P95 降至 180ms",
            "补充量化结果",
            "候选人回答",
            1,
            "pending",
            null
        );
        when(resumeImprovementService.list(42L, 9L, 7L)).thenReturn(List.of(improvement));
        when(resumeImprovementService.accept(42L, 11L)).thenReturn(new ResumeImprovementDecisionView(
            new ResumeImprovementView(
                11L, 9L, 7L, improvement.targetPath(), improvement.currentText(),
                improvement.proposedText(), improvement.rationale(), improvement.evidence(),
                1, "accepted", 2
            ),
            view(9L, 2)
        ));
        when(resumeImprovementService.reject(42L, 12L)).thenReturn(new ResumeImprovementView(
            12L, 9L, 7L, "summary", "", "新摘要", "补全摘要", "候选人回答",
            1, "rejected", null
        ));

        mockMvc.perform(get("/api/resume/9/improvements")
                .param("sessionId", "7")
                .header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].evidence").value("候选人回答"))
            .andExpect(jsonPath("$.data[0].status").value("pending"));

        mockMvc.perform(post("/api/resume/improvements/11/accept")
                .header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.improvement.status").value("accepted"))
            .andExpect(jsonPath("$.data.resume.documentVersion").value(2));

        mockMvc.perform(post("/api/resume/improvements/12/reject")
                .header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("rejected"));
    }

    @Test
    void listsAndDeletesThroughApplicationUseCases() throws Exception {
        when(listResumes.execute(42L)).thenReturn(List.of(new ResumeRepository.ResumeListItem(
            9L, "resume.pdf", LocalDateTime.of(2026, 7, 12, 10, 0), 2L
        )));

        mockMvc.perform(get("/api/resume/list").header("Authorization", "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sessionCount").value(2))
            .andExpect(jsonPath("$.data[0].inUse").value(true));
        mockMvc.perform(delete("/api/resume/9").header("Authorization", "Bearer token"))
            .andExpect(status().isOk());

        verify(deleteResume).execute(42L, 9L);
    }

    private ResumeDocumentView view(Long id, int version) {
        return new ResumeDocumentView(id, "我的简历", version, "editor", document);
    }
}
