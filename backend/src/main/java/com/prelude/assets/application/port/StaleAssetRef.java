package com.prelude.assets.application.port;

/**
 * The stored-object facts a cleanup pass needs. Deliberately not the row type: the
 * asset row still doubles as the domain object here, so naming it would leak the
 * persistence package into the use case.
 */
public record StaleAssetRef(Long assetId, String objectKey) {
}
