package com.prelude.assets.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * S3-compatible object storage configuration. The endpoint is used for SDK
 * traffic; the public endpoint is used for presigned URLs that browsers
 * must be able to reach directly.
 */
@Configuration
@EnableConfigurationProperties({
    S3StorageConfiguration.S3StorageProperties.class
})
public class S3StorageConfiguration {

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

    @Bean
    public S3ObjectStorageAdapter s3ObjectStorageAdapter(S3StorageProperties properties) {
        return new S3ObjectStorageAdapter(properties);
    }
}
