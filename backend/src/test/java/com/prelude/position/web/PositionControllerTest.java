package com.prelude.position.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.position.application.PositionService;
import com.prelude.position.domain.Position;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PositionControllerTest {

    private final PositionService positionService = mock(PositionService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PositionController(positionService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void listMarksOnlyAccountOwnedPositionsAsEditable() throws Exception {
        when(positionService.listPositions()).thenReturn(List.of(
            position(1L, null, "system"),
            position(2L, 9L, "custom")
        ));

        mockMvc.perform(get("/api/position/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].editable").value(false))
            .andExpect(jsonPath("$.data[1].id").value(2))
            .andExpect(jsonPath("$.data[1].editable").value(true))
            .andExpect(jsonPath("$.data[1].systemPrompt").value("custom"));
    }

    @Test
    void createRejectsBlankNameBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/position")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"systemPrompt\":\"custom\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(positionService);
    }

    @Test
    void createRejectsBlankInterviewFocusBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/position")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Java 后端工程师\",\"systemPrompt\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(positionService);
    }

    @Test
    void createForwardsTheDecodedRequestBodyToTheService() throws Exception {
        when(positionService.createPosition("Java 后端工程师", "重点考察并发与缓存"))
            .thenReturn(position(7L, 9L, "重点考察并发与缓存"));

        mockMvc.perform(post("/api/position")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Java 后端工程师\",\"systemPrompt\":\"重点考察并发与缓存\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(7))
            .andExpect(jsonPath("$.data.editable").value(true));

        verify(positionService).createPosition("Java 后端工程师", "重点考察并发与缓存");
    }

    @Test
    void updateUsesThePathIdentifierAsTheOwnershipScope() throws Exception {
        when(positionService.updatePosition(12L, "高级前端工程师", "render"))
            .thenReturn(position(12L, 9L, "render"));

        mockMvc.perform(put("/api/position/12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"高级前端工程师\",\"systemPrompt\":\"render\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(12))
            .andExpect(jsonPath("$.data.editable").value(true));

        verify(positionService).updatePosition(12L, "高级前端工程师", "render");
    }

    @Test
    void deleteReportsBadRequestWhenTheBuiltInPositionIsNotOwned() throws Exception {
        doThrow(BusinessException.badRequest("岗位不存在或不可编辑"))
            .when(positionService).deletePosition(1L);

        mockMvc.perform(delete("/api/position/1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void deleteReturnsAnEmptySuccessEnvelope() throws Exception {
        mockMvc.perform(delete("/api/position/8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(positionService).deletePosition(8L);
    }

    private static Position position(Long id, Long accountId, String systemPrompt) {
        Position position = new Position();
        position.setId(id);
        position.setAccountId(accountId);
        position.setName("岗位");
        position.setSystemPrompt(systemPrompt);
        return position;
    }
}
