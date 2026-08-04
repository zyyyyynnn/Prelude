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
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw BusinessException.badRequest("头像仅支持真实 JPEG 或 PNG 图片");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                ByteArrayOutputStream canonical = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", canonical)) {
                    throw invalidImage();
                }
                return new ProcessedAvatar(canonical.toByteArray(), "image/png", "png", width, height);
            } catch (BusinessException exception) {
                throw exception;
            } catch (IOException | RuntimeException exception) {
                throw invalidImage();
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage();
        }
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

    private BusinessException invalidImage() {
        return BusinessException.badRequest("头像不是可识别的 JPEG 或 PNG 图片");
    }
}
