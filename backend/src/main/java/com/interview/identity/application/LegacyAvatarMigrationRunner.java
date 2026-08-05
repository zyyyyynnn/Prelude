package com.interview.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "prelude.legacy-avatar-migration",
    name = "enabled",
    havingValue = "true"
)
public class LegacyAvatarMigrationRunner {

    private final LegacyAvatarMigrationService migrationService;
    private final LegacyAvatarMigrationProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateAfterStartup() {
        CompletableFuture.runAsync(() -> migrationService.migrateBatch(properties.getBatchSize()));
    }
}
