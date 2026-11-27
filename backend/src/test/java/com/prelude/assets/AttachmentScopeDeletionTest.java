package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.application.port.AssetRepository;
import com.prelude.assets.application.port.AssetRepository.AssetRow;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.assets.application.port.AttachmentStorage.AttachmentRow;
import com.prelude.identity.api.CurrentAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttachmentScopeDeletionTest {

    private final AttachmentStorage attachmentStorage = mock(AttachmentStorage.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final AssetDeletionService assetDeletionService = mock(AssetDeletionService.class);
    private final ObjectStoragePort objectStoragePort = mock(ObjectStoragePort.class);
    private final CurrentAccount currentAccount = mock(CurrentAccount.class);
    private final AttachmentService service = new AttachmentService(
        attachmentStorage, assetRepository, assetDeletionService, null, null, objectStoragePort, null,
        currentAccount);

    @BeforeEach
    void setUp() {
        when(currentAccount.requireId()).thenReturn(7L);
        when(attachmentStorage.deleteUnboundOwned(7L, 1L)).thenReturn(1);
        when(attachmentStorage.deleteScope(7L, "interview", 42L)).thenReturn(List.of(
            new AttachmentRow(1L, 7L, 100L, "a.txt", "a", "interview", 42L),
            new AttachmentRow(2L, 7L, 101L, "b.txt", "b", "interview", 42L)
        ));
        when(assetRepository.findByIds(List.of(100L, 101L))).thenReturn(List.of(
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

        verifyNoInteractions(assetRepository, assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteScopeRejectsAnAssetOwnedByAnotherAccount() {
        when(assetRepository.findByIds(List.of(100L, 101L))).thenReturn(List.of(
            asset(100L, 8L, "key-a"),
            asset(101L, 7L, "key-b")
        ));

        assertThatThrownBy(() -> service.deleteScope(7L, "interview", 42L))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteScopeRejectsMissingAssetMetadataInsteadOfOrphaningAnObject() {
        when(assetRepository.findByIds(List.of(100L, 101L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteScope(7L, "interview", 42L))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteUnboundDeletesMetadataAndRegistersDurableDeletionWithoutTouchingStorage() {
        AttachmentRow stored = new AttachmentRow(1L, 7L, 100L, "a.txt", "a", null, null);
        AssetRow asset = asset(100L, 7L, "key-a");
        when(attachmentStorage.findUnboundOwned(7L, 1L)).thenReturn(stored);
        when(assetRepository.findById(100L)).thenReturn(asset);

        service.deleteUnbound(1L);

        InOrder order = inOrder(attachmentStorage, assetRepository, assetDeletionService);
        order.verify(attachmentStorage).findUnboundOwned(7L, 1L);
        order.verify(assetRepository).findById(100L);
        order.verify(attachmentStorage).deleteUnboundOwned(7L, 1L);
        order.verify(assetDeletionService).register(asset);
        verifyNoInteractions(objectStoragePort);
    }

    @Test
    void deleteUnboundRejectsMissingAssetWithoutDeletingAttachmentMetadata() {
        when(attachmentStorage.findUnboundOwned(7L, 1L)).thenReturn(
            new AttachmentRow(1L, 7L, 100L, "a.txt", "a", null, null));

        assertThatThrownBy(() -> service.deleteUnbound(1L)).isInstanceOf(BusinessException.class);

        verify(attachmentStorage, never()).deleteUnboundOwned(7L, 1L);
        verifyNoInteractions(assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteUnboundDoesNotRemoveAnAttachmentBoundByConcurrentStart() {
        when(attachmentStorage.findUnboundOwned(7L, 1L)).thenReturn(
            new AttachmentRow(1L, 7L, 100L, "a.txt", "a", null, null));
        when(assetRepository.findById(100L)).thenReturn(asset(100L, 7L, "key-a"));
        when(attachmentStorage.deleteUnboundOwned(7L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteUnbound(1L)).isInstanceOf(BusinessException.class);

        verifyNoInteractions(assetDeletionService, objectStoragePort);
    }

    @Test
    void deleteUnboundPropagatesDeletionRegistrationFailure() {
        AttachmentRow stored = new AttachmentRow(1L, 7L, 100L, "a.txt", "a", null, null);
        AssetRow asset = asset(100L, 7L, "key-a");
        when(attachmentStorage.findUnboundOwned(7L, 1L)).thenReturn(stored);
        when(assetRepository.findById(100L)).thenReturn(asset);
        IllegalStateException failure = new IllegalStateException("registration unavailable");
        doThrow(failure).when(assetDeletionService).register(asset);

        assertThatThrownBy(() -> service.deleteUnbound(1L)).isSameAs(failure);

        verify(attachmentStorage).deleteUnboundOwned(7L, 1L);
        verifyNoInteractions(objectStoragePort);
    }

    private AssetRow asset(long id, long accountId, String objectKey) {
        return new AssetRow(id, accountId, "attachment", objectKey, "text/plain", 1L, "READY");
    }
}
