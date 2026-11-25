package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.assets.application.port.AttachmentStorage.AttachmentRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttachmentScopeDeletionTest {

    private final AttachmentStorage attachmentStorage = mock(AttachmentStorage.class);
    private final AssetLookup assetLookup = mock(AssetLookup.class);
    private final AssetDeletionService assetDeletionService = mock(AssetDeletionService.class);
    private final ObjectStoragePort objectStoragePort = mock(ObjectStoragePort.class);
    private final AttachmentService service = new AttachmentService(
        attachmentStorage, assetLookup, assetDeletionService, null, null, objectStoragePort, null, null);

    @BeforeEach
    void setUp() {
        when(attachmentStorage.deleteScope(7L, "interview", 42L)).thenReturn(List.of(
            new AttachmentRow(1L, 7L, 100L, "a.txt", "a", "interview", 42L),
            new AttachmentRow(2L, 7L, 101L, "b.txt", "b", "interview", 42L)
        ));
        when(assetLookup.findByIds(List.of(100L, 101L))).thenReturn(List.of(
            asset(100L, 7L, "key-a"),
            asset(101L, 7L, "key-b")
        ));
    }

    @Test
    void deleteScopeRegistersEveryOwnedAssetWithoutDeletingObjectsInline() {
        service.deleteScope(7L, "interview", 42L);

        verify(attachmentStorage).deleteScope(7L, "interview", 42L);
        verify(assetDeletionService).register(asset(100L, 7L, "key-a"));
        verify(assetDeletionService).register(asset(101L, 7L, "key-b"));
        verifyNoInteractions(objectStoragePort);
    }

    @Test
    void deleteScopeIsIdempotentWhenTheScopeHasNoAttachmentsLeft() {
        when(attachmentStorage.deleteScope(7L, "interview", 42L)).thenReturn(List.of());

        service.deleteScope(7L, "interview", 42L);

        verifyNoInteractions(assetLookup, assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteScopeRejectsAnAssetOwnedByAnotherAccount() {
        when(assetLookup.findByIds(List.of(100L, 101L))).thenReturn(List.of(
            asset(100L, 8L, "key-a"),
            asset(101L, 7L, "key-b")
        ));

        assertThatThrownBy(() -> service.deleteScope(7L, "interview", 42L))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteScopeRejectsMissingAssetMetadataInsteadOfOrphaningAnObject() {
        when(assetLookup.findByIds(List.of(100L, 101L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteScope(7L, "interview", 42L))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(assetDeletionService, objectStoragePort);
    }

    private AssetRow asset(long id, long accountId, String objectKey) {
        return new AssetRow(id, accountId, "attachment", objectKey, "text/plain", 1L, "READY");
    }
}
