package com.interview.identity.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "prelude.legacy-avatar-migration")
public class LegacyAvatarMigrationProperties {

    private boolean enabled;
    private int batchSize = 100;
}
