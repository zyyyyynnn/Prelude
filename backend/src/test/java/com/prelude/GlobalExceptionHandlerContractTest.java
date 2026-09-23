package com.prelude;

import com.prelude.position.application.PositionService;
import com.prelude.position.web.PositionController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that request-level failures keep their own protocol status instead of
 * collapsing into the catch-all handler.
 */
class GlobalExceptionHandlerContractTest {

    private final PositionService positionService = org.mockito.Mockito.mock(PositionService.class);
    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new PositionController(positionService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void malformedJsonBodyIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/position")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void nonNumericPathIdIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/position/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void unsupportedMethodIsRejectedAsMethodNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/position/list"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("method_not_allowed"));
    }

    @Test
    void businessExceptionKeepsItsOwnStatusAndCode() throws Exception {
        org.mockito.Mockito.when(positionService.listPositions())
            .thenThrow(BusinessException.of(404, "position_missing", "岗位不存在"));

        mockMvc.perform(get("/api/position/list"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("position_missing"))
            .andExpect(jsonPath("$.detail").value("岗位不存在"));
    }

    @Test
    void unexpectedFailureStaysAnInternalError() throws Exception {
        org.mockito.Mockito.when(positionService.listPositions())
            .thenThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(get("/api/position/list"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal_error"))
            .andExpect(jsonPath("$.detail").value("服务器内部错误"));
    }
}
