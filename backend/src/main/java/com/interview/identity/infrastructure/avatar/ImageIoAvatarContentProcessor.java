package com.interview.identity.infrastructure.avatar;

import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ImageIoAvatarContentProcessor implements AvatarContentProcessor {

    private final AvatarStorageProperties properties;

    @Override
    public ProcessedAvatar process(AvatarUpload upload) {
        return processInternal(upload, false);
    }

    @Override
    public ProcessedAvatar processLegacy(AvatarUpload upload) {
        return processInternal(upload, true);
    }

    private ProcessedAvatar processInternal(AvatarUpload upload, boolean allowLegacyFormats) {
        if (upload == null || upload.contentLength() == 0) {
            throw BusinessException.badRequest("请选择头像文件");
        }
        if (upload.contentLength() > properties.getAvatarMaxBytes()) {
            throw BusinessException.badRequest("头像文件超过大小限制");
        }

        byte[] source = readBounded(upload.content());
        if (source.length == 0) {
            throw BusinessException.badRequest("请选择头像文件");
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (imageInput == null) {
                throw invalidImage(allowLegacyFormats);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                if (allowLegacyFormats && LegacyWebpValidator.isValid(source)) {
                    throw BusinessException.badRequest("历史 WebP 头像缺少可靠解码器，保留原资源");
                }
                throw invalidImage(allowLegacyFormats);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!isAccepted(format, allowLegacyFormats)) {
                    throw invalidImage(allowLegacyFormats);
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);

                // Legacy GIFs deliberately take frame zero. New uploads never enter this branch.
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage(allowLegacyFormats);
                }
                ByteArrayOutputStream canonical = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", canonical)) {
                    throw invalidImage(allowLegacyFormats);
                }
                return new ProcessedAvatar(canonical.toByteArray(), "image/png", "png", width, height);
            } catch (BusinessException exception) {
                throw exception;
            } catch (IOException | RuntimeException exception) {
                throw invalidImage(allowLegacyFormats);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage(allowLegacyFormats);
        }
    }

    private boolean isAccepted(String format, boolean allowLegacyFormats) {
        if (format.equals("jpeg") || format.equals("jpg") || format.equals("png")) {
            return true;
        }
        return allowLegacyFormats && format.equals("gif");
    }

    private byte[] readBounded(InputStream input) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > properties.getAvatarMaxBytes()) {
                    throw BusinessException.badRequest("头像文件超过大小限制");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.badRequest("头像文件读取失败");
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw BusinessException.badRequest("头像尺寸不正确");
        }
        if (width > properties.getAvatarMaxWidth() || height > properties.getAvatarMaxHeight()) {
            throw BusinessException.badRequest("头像尺寸超过限制");
        }
        if ((long) width * height > properties.getAvatarMaxPixels()) {
            throw BusinessException.badRequest("头像像素数超过限制");
        }
    }

    private BusinessException invalidImage(boolean allowLegacyFormats) {
        return BusinessException.badRequest(
            allowLegacyFormats ? "历史头像不是可识别的图片" : "头像不是可识别的 JPEG 或 PNG 图片"
        );
    }
}
