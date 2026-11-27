package com.prelude.assets;

import com.prelude.assets.application.port.AssetDeletionStore;
import com.prelude.assets.application.port.AssetDeletionStore.DeletionRequest;
import com.prelude.assets.application.port.AssetRepository;
import com.prelude.assets.application.port.AssetRepository.AssetRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssetDeletionWorkerTest {

    private final AssetDeletionStore deletionStore = mock(AssetDeletionStore.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final ObjectStoragePort objectStoragePort = mock(ObjectStoragePort.class);
    private final AssetDeletionProperties properties = new AssetDeletionProperties(
        7, Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMinutes(5));
    private final AssetDeletionService service = new AssetDeletionService(
        deletionStore, assetRepository, objectStoragePort, properties);
    private final DeletionRequest request = new DeletionRequest(1L, 10L, 100L, "object-key", 1);

    @BeforeEach
    void setUp() {
        when(assetRepository.findById(100L)).thenReturn(asset(10L, "object-key"));
    }

    @Test
    void successfulObjectDeletionCompletesTheClaimedRequest() {
        when(deletionStore.claimDue(eq(7), any(), eq(Duration.ofMinutes(5))))
            .thenReturn(List.of(request));

        service.processNextBatch();

        verify(objectStoragePort).delete("object-key");
        verify(deletionStore).complete(request);
        verify(deletionStore, never()).retry(any(), any(), any());
        verify(deletionStore, never()).block(any(), any());
    }

    @Test
    void storageFailureKeepsTheRequestAndSchedulesExponentialRetry() {
        when(deletionStore.claimDue(eq(7), any(), eq(Duration.ofMinutes(5))))
            .thenReturn(List.of(request));
        doThrow(new IllegalStateException("gateway unavailable")).when(objectStoragePort).delete("object-key");
        LocalDateTime before = LocalDateTime.now();

        service.processNextBatch();

        ArgumentCaptor<LocalDateTime> nextAttempt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deletionStore).retry(eq(request), nextAttempt.capture(), eq("IllegalStateException"));
        verify(deletionStore, never()).complete(request);
        assertThat(nextAttempt.getValue()).isBetween(before.plusSeconds(1), LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void crossAccountAssetIsBlockedBeforeObjectStorageIsTouched() {
        when(assetRepository.findById(100L)).thenReturn(asset(11L, "object-key"));
        when(deletionStore.claimDue(eq(7), any(), eq(Duration.ofMinutes(5))))
            .thenReturn(List.of(request));

        service.processNextBatch();

        verifyNoInteractions(objectStoragePort);
        verify(deletionStore).block(request, "ASSET_OWNERSHIP_OR_KEY_MISMATCH");
        verify(deletionStore, never()).complete(request);
    }

    @Test
    void changedObjectKeyIsBlockedBeforeObjectStorageIsTouched() {
        when(assetRepository.findById(100L)).thenReturn(asset(10L, "different-key"));
        when(deletionStore.claimDue(eq(7), any(), eq(Duration.ofMinutes(5))))
            .thenReturn(List.of(request));

        service.processNextBatch();

        verifyNoInteractions(objectStoragePort);
        verify(deletionStore).block(request, "ASSET_OWNERSHIP_OR_KEY_MISMATCH");
    }

    private AssetRow asset(long accountId, String objectKey) {
        return new AssetRow(100L, accountId, "attachment", objectKey, "text/plain", 4L, "READY");
    }
}
