package com.prelude.assets;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * S3-compatible object storage configuration. The endpoint is used for SDK
 * traffic; the public endpoint is used for presigned URLs that browsers
 * must be able to reach directly.
 */
@ConfigurationProperties(prefix = "prelude.storage.s3")
public record S3StorageProperties(
    String endpoint,
    String publicEndpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyle,
    Duration presignTtl
) {
    public S3StorageProperties {
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            publicEndpoint = endpoint;
        }
    }
}
