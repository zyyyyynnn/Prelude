package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.documents.api.DocumentExtractor;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.assets.persistence.AttachmentMapper;
import com.prelude.assets.persistence.StoredAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Snapshot assembly must batch-load assets once per request instead of
 * issuing one selectById per attachment row.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentSnapshotBatchTest {

    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private AssetMapper assetMapper;

    @Mock
    private AssetService assetService;
    @Mock
    private AttachmentPublication attachmentPublication;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private DocumentExtractor documentExtractor;
    @Mock
    private CurrentAccount currentAccount;

    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
            attachmentMapper, assetMapper, assetService, attachmentPublication,
            objectStoragePort, documentExtractor, currentAccount
        );
    }

    @Test
    void listLoadsAssetsInOneBatch() {
        when(attachmentMapper.selectList(any())).thenReturn(List.of(
            stored(1L, 11L, "a.pdf"),
            stored(2L, 12L, "b.pdf"),
            stored(3L, 13L, "c.pdf")
        ));
        when(assetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
            asset(11L, "application/pdf", 10L),
            asset(12L, "application/pdf", 20L),
            asset(13L, "image/png", 30L)
        ));

        List<AttachmentSnapshot> snapshots = attachmentService.list(7L, "session", 9L);

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(2).image()).isTrue();
        verify(assetMapper, times(1)).selectBatchIds(anyCollection());
        verify(assetMapper, org.mockito.Mockito.never()).selectById(anyLong());
    }

    @Test
    void listFailsWhenReferencedAssetIsMissing() {
        when(attachmentMapper.selectList(any())).thenReturn(List.of(
            stored(1L, 11L, "a.pdf"),
            stored(2L, 12L, "b.pdf")
        ));
        when(assetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
            asset(11L, "application/pdf", 10L)
        ));

        assertThatThrownBy(() -> attachmentService.list(7L, "session", 9L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("资产不存在");
    }

    private static StoredAttachment stored(Long id, Long assetId, String fileName) {
        StoredAttachment stored = new StoredAttachment();
        stored.setId(id);
        stored.setAccountId(7L);
        stored.setAssetId(assetId);
        stored.setFileName(fileName);
        stored.setScopeType("session");
        stored.setScopeId(9L);
        return stored;
    }

    private static Asset asset(Long id, String mediaType, long byteSize) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setMediaType(mediaType);
        asset.setByteSize(byteSize);
        return asset;
    }
}
