package com.interview.identity.infrastructure.avatar;

import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.shared.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalLegacyAvatarSourceTest {

    @Test
    void readsActualGifAndKeepsMissingAndInvalidResultsStable(@TempDir Path root) throws Exception {
        AvatarStorageProperties properties = properties(root);
        LocalLegacyAvatarSource source = new LocalLegacyAvatarSource(properties);
        source.afterPropertiesSet();
        Files.write(root.resolve("legacy.gif"), image("gif"));
        Files.write(root.resolve("invalid.gif"), new byte[] {1, 2, 3});

        LegacyAvatarSourcePort.ReadResult gif = source.read("legacy.gif");
        assertThat(gif.status()).isEqualTo(LegacyAvatarSourcePort.Status.SUPPORTED);
        assertThat(gif.resource().contentType()).isEqualTo("image/gif");
        gif.resource().close();
        assertThat(source.read("missing.gif").status()).isEqualTo(LegacyAvatarSourcePort.Status.MISSING);
        assertThat(source.read("invalid.gif").status()).isEqualTo(LegacyAvatarSourcePort.Status.INVALID);
    }

    @Test
    void validatesWebpContainerAndRejectsTraversal(@TempDir Path root) throws Exception {
        AvatarStorageProperties properties = properties(root);
        LocalLegacyAvatarSource source = new LocalLegacyAvatarSource(properties);
        source.afterPropertiesSet();
        Files.write(root.resolve("legacy.webp"), webpContainer());

        LegacyAvatarSourcePort.ReadResult webp = source.read("legacy.webp");
        assertThat(webp.status()).isEqualTo(LegacyAvatarSourcePort.Status.UNSUPPORTED_WEBP);
        assertThat(webp.resource().contentType()).isEqualTo("image/webp");
        webp.resource().close();
        assertThatThrownBy(() -> source.read("../legacy.webp"))
            .isInstanceOf(BusinessException.class);
    }

    private AvatarStorageProperties properties(Path root) {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setLegacyAvatarRoot(root);
        properties.setAvatarMaxBytes(1024 * 1024);
        properties.setAvatarMaxWidth(2048);
        properties.setAvatarMaxHeight(2048);
        properties.setAvatarMaxPixels(4_194_304L);
        return properties;
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }

    private byte[] webpContainer() {
        return Base64.getDecoder().decode(
            "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoCAAIAAgA0JaACdLoB+AADsAD+8Oj3/yC5YXXI1/8gP+QH/ID/+PIAAAA="
        );
    }
}
