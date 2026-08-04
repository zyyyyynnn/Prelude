package com.interview.identity.infrastructure.avatar;

import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageIoAvatarContentProcessorTest {

    @Test
    void acceptsJpegAndReturnsCanonicalPng() throws Exception {
        ImageIoAvatarContentProcessor processor = new ImageIoAvatarContentProcessor(properties());

        ProcessedAvatar result = processor.process(upload(image("jpeg", 32, 24), "avatar.svg", "text/plain"));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.width()).isEqualTo(32);
        assertThat(result.height()).isEqualTo(24);
        assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes())).getWidth()).isEqualTo(32);
    }

    @Test
    void acceptsPngAndDoesNotTrustFilenameOrMimeType() throws Exception {
        ImageIoAvatarContentProcessor processor = new ImageIoAvatarContentProcessor(properties());

        ProcessedAvatar result = processor.process(upload(image("png", 12, 12), "avatar.jpg", "application/octet-stream"));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes()))).isNotNull();
    }

    @Test
    void rejectsEmptyOversizedInvalidSvgAndUndecodableContent() throws Exception {
        AvatarStorageProperties properties = properties();
        properties.setAvatarMaxBytes(16);
        ImageIoAvatarContentProcessor processor = new ImageIoAvatarContentProcessor(properties);

        assertThatThrownBy(() -> processor.process(upload(new byte[0], "avatar.png", "image/png")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> processor.process(upload(new byte[17], "avatar.png", "image/png")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("大小");
        assertThatThrownBy(() -> processor.process(upload("<svg></svg>".getBytes(), "avatar.png", "image/png")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> processor.process(upload("not-an-image".getBytes(), "avatar.jpg", "image/jpeg")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsImagesExceedingDimensionsOrPixelBudget() throws Exception {
        AvatarStorageProperties properties = properties();
        properties.setAvatarMaxWidth(10);
        properties.setAvatarMaxHeight(10);
        properties.setAvatarMaxPixels(64);
        ImageIoAvatarContentProcessor processor = new ImageIoAvatarContentProcessor(properties);

        assertThatThrownBy(() -> processor.process(upload(image("png", 11, 4), "avatar.png", "image/png")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("尺寸");
        assertThatThrownBy(() -> processor.process(upload(image("png", 8, 9), "avatar.png", "image/png")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("像素");
    }

    private AvatarStorageProperties properties() {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setAvatarMaxBytes(1024 * 1024);
        properties.setAvatarMaxWidth(2048);
        properties.setAvatarMaxHeight(2048);
        properties.setAvatarMaxPixels(4_194_304L);
        return properties;
    }

    private AvatarUpload upload(byte[] bytes, String filename, String contentType) {
        return new AvatarUpload(filename, contentType, bytes.length, new ByteArrayInputStream(bytes));
    }

    private byte[] image(String format, int width, int height) throws Exception {
        int imageType = format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, imageType);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, new Color(120, 80, 40, 255).getRGB());
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }
}
