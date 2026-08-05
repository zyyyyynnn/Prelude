package com.interview.identity.infrastructure.avatar;

import com.interview.identity.application.AvatarObjectKeys;
import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LocalLegacyAvatarSource implements LegacyAvatarSourcePort, InitializingBean {

    private final AvatarStorageProperties properties;
    private Path legacyRoot;

    @Override
    public void afterPropertiesSet() {
        Path configuredRoot = properties.getLegacyAvatarRoot();
        try {
            legacyRoot = configuredRoot.toAbsolutePath().normalize();
            Files.createDirectories(legacyRoot);
            if (!Files.isDirectory(legacyRoot) || !Files.isReadable(legacyRoot)) {
                throw new IOException("legacy avatar root is not readable");
            }
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException("app.storage.legacy-avatar-root is not available", exception);
        }
    }

    @Override
    public ReadResult read(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            return ReadResult.missing();
        }
        try {
            long length = Files.size(target);
            if (length <= 0 || length > properties.getAvatarMaxBytes()) {
                return ReadResult.invalid();
            }
            byte[] bytes = Files.readAllBytes(target);
            if (bytes.length != length) {
                return ReadResult.invalid();
            }
            DetectedFormat format = detect(bytes);
            if (format == null) {
                return ReadResult.invalid();
            }
            LegacyAvatarResource resource = new LegacyAvatarResource(
                objectKey,
                format.contentType(),
                bytes.length,
                new ByteArrayInputStream(bytes)
            );
            return format.webP()
                ? ReadResult.unsupportedWebp(resource)
                : ReadResult.supported(resource);
        } catch (IOException | RuntimeException exception) {
            return ReadResult.invalid();
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("历史头像文件删除失败", exception);
        }
    }

    private DetectedFormat detect(byte[] bytes) throws IOException {
        if (LegacyWebpValidator.isValid(bytes)) {
            return new DetectedFormat("image/webp", true);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg")
                    && !format.equals("png") && !format.equals("gif")) {
                    return null;
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                    || width > properties.getAvatarMaxWidth()
                    || height > properties.getAvatarMaxHeight()
                    || (long) width * height > properties.getAvatarMaxPixels()) {
                    return null;
                }
                return new DetectedFormat(
                    format.equals("gif") ? "image/gif" : format.equals("png") ? "image/png" : "image/jpeg",
                    false
                );
            } finally {
                reader.dispose();
            }
        }
    }

    private Path resolve(String objectKey) {
        if (legacyRoot == null) {
            throw new IllegalStateException("Legacy avatar source has not been initialized");
        }
        String safeObjectKey = AvatarObjectKeys.requireSafe(objectKey);
        Path target = legacyRoot.resolve(safeObjectKey).normalize();
        if (!target.getParent().equals(legacyRoot)) {
            throw new IllegalArgumentException("Legacy avatar object escaped storage root");
        }
        return target;
    }

    private record DetectedFormat(String contentType, boolean webP) {
    }
}
