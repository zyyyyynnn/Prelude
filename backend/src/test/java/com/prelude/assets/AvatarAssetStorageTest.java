package com.prelude.assets;

import com.prelude.assets.application.port.AssetRepository;
import com.prelude.assets.application.port.AssetRepository.AssetRow;
import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AvatarAssetStorageTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4AWJiYGD4DwAAAP//cGajQwAAAAZJREFUAwABDgEC81VxbAAAAABJRU5ErkJggg==");
    private static final byte[] WEBP = Base64.getDecoder().decode(
        "UklGRjwAAABXRUJQVlA4WAoAAAAAAAAAAAAAAAAAADAAMQA2VlA4IBgAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v02aAA=");

    private final AssetService assetService = mock(AssetService.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final ObjectStoragePort objectStorage = mock(ObjectStoragePort.class);
    private final AssetDeletionService deletionService = mock(AssetDeletionService.class);
    private final AvatarAssetStorage storage = new AvatarAssetStorage(
        assetService, assetRepository, objectStorage, new AvatarImageValidator(), deletionService);

    @BeforeEach
    void stagePendingAsset() {
        when(assetService.createPending(7L, "avatar", "image/png", PNG.length))
            .thenReturn(new AssetRow(9L, 7L, "avatar", "object-key", "image/png", (long) PNG.length,
                "PENDING_UPLOAD"));
    }

    @Test
    void validPngIsStoredWithCanonicalMimeAndCanBecomeReady() {
        String url = storage.stage(7L, "me.png", "application/octet-stream", PNG);

        assertThat(url).isEqualTo("/api/assets/9/content");
        verify(assetService).createPending(7L, "avatar", "image/png", PNG.length);
        verify(objectStorage).put("object-key", "image/png", PNG);

        when(assetRepository.findById(9L)).thenReturn(new AssetRow(
            9L, 7L, "avatar", "object-key", "image/png", (long) PNG.length, "PENDING_UPLOAD"));
        when(assetService.markReady(9L)).thenReturn(true);
        storage.confirmReady(url);
        verify(assetService).markReady(9L);
    }

    @Test
    void discardRegistersReadyAvatarDeletionWithoutTouchingStorageOrMetadata() {
        AssetRow ready = new AssetRow(9L, 7L, "avatar", "object-key", "image/png", (long) PNG.length, "READY");
        when(assetRepository.findById(9L)).thenReturn(ready);

        storage.discard(7L, "/api/assets/9/content");

        verify(deletionService).register(ready);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void discardPropagatesDeletionRegistrationFailure() {
        AssetRow ready = new AssetRow(9L, 7L, "avatar", "object-key", "image/png", (long) PNG.length, "READY");
        when(assetRepository.findById(9L)).thenReturn(ready);
        IllegalStateException failure = new IllegalStateException("registration unavailable");
        doThrow(failure).when(deletionService).register(ready);

        assertThatThrownBy(() -> storage.discard(7L, "/api/assets/9/content")).isSameAs(failure);
    }

    @Test
    void discardDoesNotRegisterAnAvatarOwnedByAnotherAccount() {
        when(assetRepository.findById(9L)).thenReturn(new AssetRow(
            9L, 8L, "avatar", "object-key", "image/png", (long) PNG.length, "READY"));

        storage.discard(7L, "/api/assets/9/content");

        verifyNoInteractions(deletionService, objectStorage);
    }

    @Test
    void webpUsesItsDecodedCanonicalMime() {
        when(assetService.createPending(7L, "avatar", "image/webp", WEBP.length))
            .thenReturn(new AssetRow(10L, 7L, "avatar", "webp-key", "image/webp", (long) WEBP.length,
                "PENDING_UPLOAD"));

        storage.stage(7L, "me.webp", null, WEBP);

        verify(objectStorage).put("webp-key", "image/webp", WEBP);
    }

    @Test
    void jpegAndGifUseTheirDecodedCanonicalMime() throws IOException {
        byte[] jpeg = imageBytes("jpeg", 1, 1);
        when(assetService.createPending(7L, "avatar", "image/jpeg", jpeg.length))
            .thenReturn(new AssetRow(11L, 7L, "avatar", "jpeg-key", "image/jpeg", (long) jpeg.length,
                "PENDING_UPLOAD"));
        storage.stage(7L, "me.jpg", null, jpeg);
        verify(objectStorage).put("jpeg-key", "image/jpeg", jpeg);

        byte[] gif = imageBytes("gif", 1, 1);
        when(assetService.createPending(7L, "avatar", "image/gif", gif.length))
            .thenReturn(new AssetRow(12L, 7L, "avatar", "gif-key", "image/gif", (long) gif.length,
                "PENDING_UPLOAD"));
        storage.stage(7L, "me.gif", "application/octet-stream", gif);
        verify(objectStorage).put("gif-key", "image/gif", gif);
    }

    @Test
    void spoofedExtensionAndMimeAreRejectedBeforeAnAssetExists() {
        assertInvalid("me.jpg", "image/jpeg", PNG);
        assertInvalid("me.png", "image/jpeg", PNG);
        verifyNoInteractions(assetService, objectStorage);
    }

    @Test
    void illegalAndTruncatedBytesAreRejectedBeforeAnAssetExists() {
        assertInvalid("me.png", "image/png", new byte[] {1, 2, 3});
        assertInvalid("me.png", "image/png", java.util.Arrays.copyOf(PNG, PNG.length / 2));
        verifyNoInteractions(assetService, objectStorage);
    }

    @Test
    void byteLimitIsEnforcedBeforeDecoding() {
        assertInvalid("me.png", "image/png", new byte[AvatarImageValidator.MAX_BYTES + 1]);
        verifyNoInteractions(assetService, objectStorage);
    }

    @Test
    void dimensionLimitIsEnforcedBeforePixelDecoding() throws IOException {
        assertInvalid("me.png", "image/png", imageBytes("png", AvatarImageValidator.MAX_DIMENSION + 1, 1));
        verifyNoInteractions(assetService, objectStorage);
    }

    @Test
    void pixelLimitIsEnforcedIndependentlyOfTheDimensionLimit() throws IOException {
        int width = AvatarImageValidator.MAX_DIMENSION;
        int height = (int) (AvatarImageValidator.MAX_PIXELS / width) + 1;
        assertInvalid("me.png", "image/png", imageBytes("png", width, height));
        verifyNoInteractions(assetService, objectStorage);
    }

    private void assertInvalid(String fileName, String mediaType, byte[] bytes) {
        ExceptionFixtures.assertBusinessException(
            () -> storage.stage(7L, fileName, mediaType, bytes), "avatar_invalid");
    }

    private byte[] imageBytes(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IllegalStateException(format + " writer is unavailable");
        }
        return output.toByteArray();
    }
}
