package com.prelude.assets;

import com.prelude.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Component
public class AvatarImageValidator {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;
    static final long MAX_PIXELS = 8_388_608L;

    private static final Map<String, String> FORMAT_MEDIA_TYPES = Map.of(
        "jpeg", "image/jpeg",
        "png", "image/png",
        "webp", "image/webp",
        "gif", "image/gif"
    );

    public ValidatedAvatar validate(String fileName, String declaredMediaType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw invalid();
        }
        String extension = extensionOf(fileName);
        String declared = declaredMediaType == null || declaredMediaType.isBlank()
            || "application/octet-stream".equals(declaredMediaType)
            ? null
            : declaredMediaType;

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalid();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String readerFormat = reader.getFormatName();
                String format = FORMAT_MEDIA_TYPES.keySet().stream()
                    .filter(candidate -> candidate.equalsIgnoreCase(readerFormat))
                    .findFirst()
                    .orElseThrow(AvatarImageValidator::invalid);
                if (!format.equals(extension) || (declared != null && !FORMAT_MEDIA_TYPES.get(format).equals(declared))) {
                    throw invalid();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) {
                    throw invalid();
                }
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) {
                    throw invalid();
                }
                image.flush();
                return new ValidatedAvatar(FORMAT_MEDIA_TYPES.get(format), width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return "jpg".equals(extension) ? "jpeg" : extension;
    }

    private static BusinessException invalid() {
        return BusinessException.badRequest("avatar_invalid", "头像文件无效或超过限制");
    }

    public record ValidatedAvatar(String mediaType, int width, int height) {
    }
}
