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
    void schemaCanonicalizesLegacyAvatarUrisIdempotently() throws Exception {
        String schema = readSchema();

        assertThat(schema)
            .contains("`avatar_revision` BIGINT NOT NULL DEFAULT 0")
            .contains("COLUMN_NAME = 'avatar_revision'")
            .contains("CREATE TABLE IF NOT EXISTS `user`")
            .contains("CONCAT('/media/avatars/'")
            .contains("WHERE `avatar_url` LIKE '/uploads/avatars/%'")
            .doesNotContain("DROP TABLE `user`");
    }

    @Test
    void avatarUriAndRevisionContractsAreDocumentedInSchemaSource() throws Exception {
        String schema = readSchema();

        assertThat(schema).contains("`avatar_url` VARCHAR(512)");
        assertThat(schema).contains("`avatar_revision` BIGINT NOT NULL DEFAULT 0");
    }

    private static String readSchema() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(
            AvatarSchemaMaintenanceTest.class.getClassLoader().getResource("schema.sql")
        );
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
