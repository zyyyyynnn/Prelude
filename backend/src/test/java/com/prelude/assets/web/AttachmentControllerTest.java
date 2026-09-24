package com.prelude.assets.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.assets.AttachmentService;
import com.prelude.assets.api.AttachmentSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentControllerTest {

    private final AttachmentService attachmentService = mock(AttachmentService.class);

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new AttachmentController(attachmentService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void uploadReturnsOnlyTheProjectionTheComposerNeeds() throws Exception {
        when(attachmentService.upload(eq("补充材料.png"), eq("image/png"), any()))
            .thenReturn(new AttachmentSnapshot(8L, "补充材料.png", "image/png", 2048L, true, null, null));

        mockMvc.perform(multipart("/api/attachments")
                .file(new MockMultipartFile(
                    "file", "补充材料.png", "image/png", "png".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(8))
            .andExpect(jsonPath("$.data.fileName").value("补充材料.png"))
            .andExpect(jsonPath("$.data.mediaType").value("image/png"))
            .andExpect(jsonPath("$.data.size").value(2048))
            .andExpect(jsonPath("$.data.image").value(true))
            .andExpect(jsonPath("$.data.text").doesNotExist());
    }

    @Test
    void uploadReportsARejectedFileInTheSharedEnvelope() throws Exception {
        when(attachmentService.upload(any(), any(), any()))
            .thenThrow(BusinessException.badRequest("不支持的文件类型"));

        mockMvc.perform(multipart("/api/attachments")
                .file(new MockMultipartFile(
                    "file", "payload.exe", "application/octet-stream",
                    "x".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail").value("不支持的文件类型"));
    }

    @Test
    void deleteTargetsThePathAttachmentAndReturnsAnEmptyEnvelope() throws Exception {
        mockMvc.perform(delete("/api/attachments/8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(attachmentService).deleteUnbound(8L);
    }

    @Test
    void deleteRefusesAnAttachmentAlreadyBoundToASession() throws Exception {
        doThrow(BusinessException.badRequest("附件已绑定会话，无法删除"))
            .when(attachmentService).deleteUnbound(8L);

        mockMvc.perform(delete("/api/attachments/8"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"));
    }
}
