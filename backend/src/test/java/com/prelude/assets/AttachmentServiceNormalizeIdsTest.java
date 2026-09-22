package com.prelude.assets;

import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.test.ExceptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * normalizeIds guards every public entry point (requireOwned / bind run it
 * first), so a malformed id list must fail with a message naming the real
 * cause: duplicates/nulls are not a count problem, and an over-long list is
 * not a file-name problem.
 */
class AttachmentServiceNormalizeIdsTest {

    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
            new AttachmentStorage() {
                @Override
                public List<AttachmentStorage.AttachmentRow> listByScope(Long a, String s, Long i) {
                    return List.of();
                }

                @Override
                public AttachmentRow findUnboundOwned(Long a, Long id) {
                    return null;
                }

                @Override
                public List<AttachmentRow> findUnboundOwned(Long a, List<Long> ids) {
                    throw new AssertionError("normalizeIds must reject the list before any storage lookup");
                }

                @Override
                public int bindToScope(Long a, List<Long> ids, String s, Long i) {
                    throw new AssertionError("normalizeIds must reject the list before any storage lookup");
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
                    return List.of();
                }

                @Override
                public AssetLookup.AssetRow findById(Long id) {
                    return null;
                }

                @Override
                public void deleteById(Long id) {
                }
            },
            null, null, null, null, null
        );
    }

    @Test
    void duplicateIdsAreRejectedAsDuplicatesNotAsOverLimit() {
        ExceptionFixtures.assertBusinessException(
            () -> attachmentService.requireOwned(7L, List.of(1L, 2L, 2L)))
            .hasMessageContaining("重复")
            .hasMessageNotContaining("最多");
    }

    @Test
    void nullIdsAreRejectedAsInvalidNotAsOverLimit() {
        ExceptionFixtures.assertBusinessException(
            () -> attachmentService.requireOwned(7L, Arrays.asList(1L, null)))
            .hasMessageContaining("无效")
            .hasMessageNotContaining("最多");
    }

    @Test
    void sixDistinctIdsAreRejectedAsOverLimitNotAsBadFileName() {
        ExceptionFixtures.assertBusinessException(
            () -> attachmentService.requireOwned(7L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .hasMessageContaining("最多")
            .hasMessageNotContaining("文件名");
    }
}
