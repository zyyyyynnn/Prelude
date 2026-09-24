package com.prelude.assets;

import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.test.AssetFixtures;
import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Snapshot assembly must batch-load assets once per request instead of
 * issuing one selectById per attachment row.
 */
class AttachmentSnapshotBatchTest {

    private final java.util.List<AttachmentStorage.AttachmentRow> attachments = new java.util.ArrayList<>();
    private final java.util.Map<Long, AssetLookup.AssetRow> assets = new java.util.HashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger assetBatchLoads =
        new java.util.concurrent.atomic.AtomicInteger();
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
            new AttachmentStorage() {
                @Override
                public List<AttachmentStorage.AttachmentRow> listByScope(Long a, String s, Long i) {
                    return List.copyOf(attachments);
                }

                @Override
                public AttachmentRow findUnboundOwned(Long a, Long id) {
                    return null;
                }

                @Override
                public List<AttachmentRow> findUnboundOwned(Long a, List<Long> ids) {
                    return List.of();
                }

                @Override
                public int bindToScope(Long a, List<Long> ids, String s, Long i) {
                    return 0;
                }

                @Override
                public void unbindScope(Long a, String s, Long i) {
                }

                @Override
                public AttachmentRow insert(AttachmentRow stored) {
                    return stored;
                }

                @Override
                public void deleteById(Long id) {
                }
            },
            new AssetLookup() {
                @Override
                public List<AssetLookup.AssetRow> findByIds(List<Long> ids) {
                    assetBatchLoads.incrementAndGet();
                    return ids.stream().map(assets::get).filter(java.util.Objects::nonNull).toList();
                }

                @Override
                public AssetLookup.AssetRow findById(Long id) {
                    return assets.get(id);
                }

                @Override
                public void deleteById(Long id) {
                }
            },
            null, null, null, null, null
        );
    }

    @Test
    void listLoadsAssetsInOneBatch() {
        attachments.addAll(List.of(
            AssetFixtures.attachmentRow(1L, 11L, "a.pdf"),
            AssetFixtures.attachmentRow(2L, 12L, "b.pdf"),
            AssetFixtures.attachmentRow(3L, 13L, "c.pdf")
        ));
        assets.putAll(java.util.Map.of(
            11L, AssetFixtures.assetRow(11L, "application/pdf", 10L),
            12L, AssetFixtures.assetRow(12L, "application/pdf", 20L),
            13L, AssetFixtures.assetRow(13L, "image/png", 30L)
        ));

        List<AttachmentSnapshot> snapshots = attachmentService.list(7L, "session", 9L);

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(2).image()).isTrue();
        assertThat(assetBatchLoads).hasValue(1);
    }

    @Test
    void listFailsWhenReferencedAssetIsMissing() {
        attachments.addAll(List.of(
            AssetFixtures.attachmentRow(1L, 11L, "a.pdf"),
            AssetFixtures.attachmentRow(2L, 12L, "b.pdf")
        ));
        assets.put(11L, AssetFixtures.assetRow(11L, "application/pdf", 10L));

        ExceptionFixtures.assertBusinessException(() -> attachmentService.list(7L, "session", 9L))
            .hasMessageContaining("素材不存在");
    }
}
