package com.interview.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarSchemaMaintenanceTest {

    @Test
    void legacyAvatarUrisUpgradeIdempotentlyWithoutChangingEmptyValues() throws Exception {
        String schema = readSchema();

        assertThat(schema)
            .contains("CONCAT('/media/avatars/', SUBSTRING_INDEX(`avatar_url`, '/uploads/avatars/', -1))")
            .contains("WHERE `avatar_url` LIKE '/uploads/avatars/%'")
            .contains("AND `avatar_url` NOT LIKE '/media/avatars/%'")
            .contains("CREATE TABLE IF NOT EXISTS `user`")
            .doesNotContain("DROP TABLE `user`");
    }

    @Test
    void canonicalAvatarUriAndLocalStorageContractAreDocumentedInSchemaSource() throws Exception {
        String schema = readSchema();

        assertThat(schema).contains("`avatar_url` VARCHAR(512)");
        assertThat(schema.indexOf("UPDATE `user`"))
            .isGreaterThan(schema.indexOf("COLUMN_NAME = 'avatar_url'"));
    }

    private static String readSchema() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(
            AvatarSchemaMaintenanceTest.class.getClassLoader().getResource("schema.sql")
        );
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
