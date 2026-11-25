package com.prelude.assets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssetDeletionProperties.class)
class AssetDeletionConfiguration {
}
