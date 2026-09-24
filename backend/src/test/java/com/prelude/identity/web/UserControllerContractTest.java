package com.prelude.identity.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.identity.api.UserProfileRequest;
import com.prelude.identity.api.UserProfileResponse;
import com.prelude.identity.application.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerContractTest {

    private final ProfileService profileService = mock(ProfileService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(profileService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void profileReadWrapsTheServiceViewInSuccessEnvelope() throws Exception {
        when(profileService.getCurrentUserProfile())
            .thenReturn(new UserProfileResponse(9L, "demo", "demo@prelude.local", "/a.png", "system", 4L));

        mockMvc.perform(get("/api/user/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.accountId").value(9))
            .andExpect(jsonPath("$.data.avatarUrl").value("/a.png"))
            .andExpect(jsonPath("$.data.revision").value(4));
    }

    @Test
    void profileWriteRequiresRevisionAndOperationIdentity() throws Exception {
        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(profileService);
    }

    @Test
    void profileWriteRejectsMalformedEmailBeforeTheServiceSeesIt() throws Exception {
        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"expectedRevision\":4,\"operationId\":\"op-1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        verifyNoInteractions(profileService);
    }

    @Test
    void profileWriteSurfacesStaleRevisionAsConflict() throws Exception {
        when(profileService.updateCurrentUserProfile(any(UserProfileRequest.class)))
            .thenThrow(BusinessException.revisionConflict("资料已被其他操作更新"));

        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo\",\"expectedRevision\":3,\"operationId\":\"op-1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("revision_conflict"));
    }

    @Test
    void avatarUploadForwardsTheMultipartFilePartToTheService() throws Exception {
        when(profileService.updateAvatar(any()))
            .thenReturn(new UserProfileResponse(9L, "demo", null, "/staged.png", "system", 5L));

        mockMvc.perform(multipart("/api/user/avatar")
                .file(new MockMultipartFile(
                    "file", "me.png", "image/png", "binary".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.avatarUrl").value("/staged.png"))
            .andExpect(jsonPath("$.data.revision").value(5));

        verify(profileService).updateAvatar(any());
    }
}
