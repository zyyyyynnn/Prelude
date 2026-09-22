package com.prelude.assets.application.port;

import java.util.List;

/**
 * Asset row access. The asset row still doubles as the domain object in this module,
 * so the port speaks in {@link AssetRow} rather than naming the persistence package.
 */
public interface AssetLookup {

    AssetRow findById(Long assetId);

    List<AssetRow> findByIds(List<Long> assetIds);

    void deleteById(Long assetId);

    /** The asset row as the service sees it. */
    record AssetRow(
        Long id,
        Long accountId,
        String kind,
        String objectKey,
        String mediaType,
        Long byteSize,
        String status
    ) {
    }
}
