package com.interview.identity.api;

import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AvatarMediaControllerWebMvcTest {

    @Mock
    private AvatarStoragePort avatarStoragePort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AvatarMediaController(avatarStoragePort))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void servesAvatarWithStableMediaHeaders() throws Exception {
        String objectKey = "42_550e8400-e29b-41d4-a716-446655440000.png";
        when(avatarStoragePort.open(objectKey)).thenReturn(Optional.of(new AvatarStoragePort.StoredResource(
            objectKey,
            "image/png",
            7,
            new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8))
        )));

        mockMvc.perform(get("/media/avatars/" + objectKey))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Content-Length", "7"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
    }

    @Test
    void missingAvatarReturns404InsteadOfSpaHtml() throws Exception {
        when(avatarStoragePort.open("missing.png")).thenReturn(Optional.empty());

        mockMvc.perform(get("/media/avatars/missing.png"))
            .andExpect(status().isNotFound())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")));
    }
}
