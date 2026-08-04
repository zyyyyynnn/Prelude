package com.interview.identity.infrastructure.avatar;

import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAvatarStorageTest {

    @TempDir
    Path tempDirectory;

    private LocalAvatarStorage storage;

    @BeforeEach
    void setUp() {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setAvatarRoot(tempDirectory.resolve("avatars"));
        properties.setAvatarPublicPrefix("/media/avatars");
        storage = new LocalAvatarStorage(properties);
        storage.afterPropertiesSet();
    }

    @Test
    void storesAtomicallyOpensAndDeletesOnlySafeObjects() throws Exception {
        ProcessedAvatar avatar = new ProcessedAvatar(
            "canonical".getBytes(StandardCharsets.UTF_8),
            "image/png",
            "png",
            1,
            1
        );

        AvatarStoragePort.StoredAvatar stored = storage.store("42_550e8400-e29b-41d4-a716-446655440000.png", avatar);

        assertThat(stored.publicUri()).isEqualTo("/media/avatars/42_550e8400-e29b-41d4-a716-446655440000.png");
        assertThat(Files.readString(tempDirectory.resolve("avatars").resolve(stored.objectKey())))
            .isEqualTo("canonical");
        try (AvatarStoragePort.StoredResource resource = storage.open(stored.objectKey()).orElseThrow()) {
            assertThat(resource.contentType()).isEqualTo("image/png");
            assertThat(resource.contentLength()).isEqualTo("canonical".length());
        }

        storage.delete(stored.objectKey());
        assertThat(storage.open(stored.objectKey())).isEmpty();
    }

    @Test
    void rejectsTraversalAndNeverOverwritesAnExistingObject() {
        ProcessedAvatar avatar = new ProcessedAvatar(new byte[] {1}, "image/png", "png", 1, 1);
        String objectKey = "42_550e8400-e29b-41d4-a716-446655440000.png";
        storage.store(objectKey, avatar);

        assertThatThrownBy(() -> storage.store(objectKey, avatar))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> storage.open("..\\secret.txt"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.open("../secret.txt"))
            .isInstanceOf(BusinessException.class);
    }
}
