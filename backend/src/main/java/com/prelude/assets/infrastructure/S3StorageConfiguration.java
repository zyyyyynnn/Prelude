package com.prelude.assets.infrastructure;

import com.prelude.assets.S3StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the S3-compatible object storage adapter.
 */
@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfiguration {

    @Bean
    public S3ObjectStorageAdapter s3ObjectStorageAdapter(S3StorageProperties properties) {
        return new S3ObjectStorageAdapter(properties);
    }
}
