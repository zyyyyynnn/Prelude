package com.interview.identity.infrastructure.avatar;

import com.interview.identity.application.AvatarObjectKeys;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.ProcessedAvatar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAvatarStorage implements AvatarStoragePort, InitializingBean {

    private final AvatarStorageProperties properties;
    private Path avatarRoot;

    @Override
    public void afterPropertiesSet() {
        Path configuredRoot = properties.getAvatarRoot();
        try {
            Path normalizedRoot = configuredRoot.toAbsolutePath().normalize();
            Files.createDirectories(normalizedRoot);
            if (!Files.isDirectory(normalizedRoot)
                || !Files.isReadable(normalizedRoot)
                || !Files.isWritable(normalizedRoot)) {
                throw new IOException("directory is not readable and writable");
            }
            avatarRoot = normalizedRoot;
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException(
                "app.storage.avatar-root is not available: " + configuredRoot,
                exception
            );
        }
    }

    @Override
    public StoredAvatar store(String objectKey, ProcessedAvatar avatar) {
        Path target = resolve(objectKey);
        if (Files.exists(target)) {
            throw new IllegalStateException("Avatar object already exists: " + objectKey);
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(avatarRoot, ".avatar-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(avatar.bytes());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            temporary = null;
            return new StoredAvatar(objectKey, publicUri(objectKey));
        } catch (IOException | RuntimeException exception) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupException) {
                    log.warn("avatar temporary file cleanup failed", cleanupException);
                }
            }
            throw new IllegalStateException("头像文件存储失败", exception);
        }
    }

    @Override
    public Optional<StoredResource> open(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            String contentType = contentType(objectKey);
            if (contentType == null) {
                return Optional.empty();
            }
            return Optional.of(new StoredResource(
                objectKey,
                contentType,
                Files.size(target),
                Files.newInputStream(target, StandardOpenOption.READ)
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("头像资源读取失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("头像文件删除失败", exception);
        }
    }

    private Path resolve(String objectKey) {
        if (avatarRoot == null) {
            throw new IllegalStateException("Avatar storage has not been initialized");
        }
        String safeObjectKey = AvatarObjectKeys.requireSafe(objectKey);
        Path target = avatarRoot.resolve(safeObjectKey).normalize();
        if (!target.getParent().equals(avatarRoot)) {
            throw new IllegalArgumentException("Avatar object escaped storage root");
        }
        return target;
    }

    private String publicUri(String objectKey) {
        String prefix = properties.getAvatarPublicPrefix();
        String normalizedPrefix = prefix.startsWith("/") ? prefix : "/" + prefix;
        normalizedPrefix = normalizedPrefix.endsWith("/")
            ? normalizedPrefix.substring(0, normalizedPrefix.length() - 1)
            : normalizedPrefix;
        return normalizedPrefix + "/" + objectKey;
    }

    private String contentType(String objectKey) {
        String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> null;
        };
    }
}
