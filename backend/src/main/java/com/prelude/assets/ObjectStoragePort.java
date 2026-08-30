package com.prelude.assets;

import java.time.Duration;

/**
 * Port isolating the S3-compatible object storage infrastructure.
 * The AWS SDK types must not leak beyond this interface.
 */
public interface ObjectStoragePort {

    void put(String objectKey, String mediaType, byte[] bytes);

    byte[] get(String objectKey);

    void delete(String objectKey);

    String presignGet(String objectKey, Duration ttl);
}
