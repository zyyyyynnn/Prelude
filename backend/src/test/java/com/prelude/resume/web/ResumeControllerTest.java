package com.prelude.resume.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.resume.application.DeleteResume;
import com.prelude.resume.application.ImportResumePdf;
import com.prelude.resume.application.ImportResumeResult;
import com.prelude.resume.application.ListResumes;
import com.prelude.resume.application.port.ResumeParser.ParsedProject;
import com.prelude.resume.application.port.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeControllerTest {

    private final ImportResumePdf importResumePdf = mock(ImportResumePdf.class);
    private final ListResumes listResumes = mock(ListResumes.class);
    private final DeleteResume deleteResume = mock(DeleteResume.class);
    private final CurrentAccount currentAccount = mock(CurrentAccount.class);

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new ResumeController(importResumePdf, listResumes, deleteResume, currentAccount))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void uploadImportsUnderTheAuthenticatedAccountAndMapsTheParsedProjects() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        when(importResumePdf.execute(eq(9L), eq("简历.pdf"), any()))
            .thenReturn(new ImportResumeResult(
                42L,
                List.of("Java", "MySQL"),
                List.of(new ParsedProject("订单服务", "负责下单链路"))
            ));

        mockMvc.perform(multipart("/api/resume/upload")
                .file(new MockMultipartFile(
                    "file", "简历.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resumeId").value(42))
            .andExpect(jsonPath("$.data.skills[0]").value("Java"))
            .andExpect(jsonPath("$.data.projects[0].name").value("订单服务"))
            .andExpect(jsonPath("$.data.projects[0].description").value("负责下单链路"));

        verify(importResumePdf).execute(eq(9L), eq("简历.pdf"), any());
    }

    @Test
    void uploadReportsTheParserProblemInTheSharedEnvelope() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        when(importResumePdf.execute(eq(9L), any(), any()))
            .thenThrow(BusinessException.badRequest("未能从文件中识别出简历内容"));

        mockMvc.perform(multipart("/api/resume/upload")
                .file(new MockMultipartFile(
                    "file", "空白.pdf", "application/pdf", "".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail").value("未能从文件中识别出简历内容"));
    }

    @Test
    void listDerivesInUseFromTheSessionCount() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        when(listResumes.execute(9L)).thenReturn(List.of(
            new ResumeRepository.ResumeListItem(1L, "在用简历.pdf", LocalDateTime.parse("2026-09-01T08:00:00"), 2L),
            new ResumeRepository.ResumeListItem(2L, "未用简历.pdf", LocalDateTime.parse("2026-09-02T08:00:00"), 0L)
        ));

        mockMvc.perform(get("/api/resume/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].inUse").value(true))
            .andExpect(jsonPath("$.data[1].id").value(2))
            .andExpect(jsonPath("$.data[1].inUse").value(false));
    }

    @Test
    void deleteScopesTheRequestToTheAuthenticatedAccount() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);

        mockMvc.perform(delete("/api/resume/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(deleteResume).execute(9L, 42L);
    }

    @Test
    void deleteReportsTheOwnershipProblemInTheSharedEnvelope() throws Exception {
        when(currentAccount.requireId()).thenReturn(9L);
        doThrow(BusinessException.badRequest("简历不存在或无权访问"))
            .when(deleteResume).execute(9L, 42L);

        mockMvc.perform(delete("/api/resume/42"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail").value("简历不存在或无权访问"));
    }
}
