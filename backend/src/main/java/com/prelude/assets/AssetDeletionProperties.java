package com.prelude.assets;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "prelude.storage.deletion")
public record AssetDeletionProperties(
    int batchSize,
    Duration retryBaseDelay,
    Duration retryMaxDelay,
    Duration leaseDuration
) {
    public AssetDeletionProperties {
        if (batchSize <= 0 || batchSize > 1_000) {
            throw new IllegalArgumentException("prelude.storage.deletion.batch-size must be between 1 and 1000");
        }
        requirePositiveDuration("retry-base-delay-ms", retryBaseDelay);
        requirePositiveDuration("retry-max-delay-ms", retryMaxDelay);
        requirePositiveDuration("lease-duration-ms", leaseDuration);
        if (retryBaseDelay.compareTo(retryMaxDelay) > 0) {
            throw new IllegalArgumentException("prelude.storage.deletion.retry-base-delay-ms must not exceed retry-max-delay-ms");
        }
    }

    private static void requirePositiveDuration(String name, Duration duration) {
        if (duration == null || duration.toMillis() < 1) {
            throw new IllegalArgumentException("prelude.storage.deletion." + name + " must be positive");
        }
    }
}
