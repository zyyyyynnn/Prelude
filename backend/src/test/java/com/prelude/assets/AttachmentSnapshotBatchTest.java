package com.prelude.assets;

import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.assets.persistence.AttachmentMapper;
import com.prelude.test.AssetFixtures;
import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Snapshot assembly must batch-load assets once per request instead of
 * issuing one selectById per attachment row.
 */
class AttachmentSnapshotBatchTest {

    private AttachmentMapper attachmentMapper;
    private AssetMapper assetMapper;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentMapper = mock(AttachmentMapper.class);
        assetMapper = mock(AssetMapper.class);
        attachmentService = new AttachmentService(
            attachmentMapper, assetMapper, null, null, null, null, null
        );
    }

    @Test
    void listLoadsAssetsInOneBatch() {
        when(attachmentMapper.selectList(any())).thenReturn(List.of(
            AssetFixtures.stored(1L, 11L, "a.pdf"),
            AssetFixtures.stored(2L, 12L, "b.pdf"),
            AssetFixtures.stored(3L, 13L, "c.pdf")
        ));
        when(assetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
            AssetFixtures.asset(11L, "application/pdf", 10L),
            AssetFixtures.asset(12L, "application/pdf", 20L),
            AssetFixtures.asset(13L, "image/png", 30L)
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
            AssetFixtures.stored(1L, 11L, "a.pdf"),
            AssetFixtures.stored(2L, 12L, "b.pdf")
        ));
        when(assetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
            AssetFixtures.asset(11L, "application/pdf", 10L)
        ));

        ExceptionFixtures.assertBusinessException(() -> attachmentService.list(7L, "session", 9L))
            .hasMessageContaining("资产不存在");
    }
}
