package com.prelude.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JavaPackageLayoutTest {

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+);");

    @Test
    void javaSourcesMatchTheirPackageDirectories() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<Path> sources = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        }

        assertThat(sources).isNotEmpty();
        for (Path source : sources) {
            Matcher matcher = PACKAGE_DECLARATION.matcher(Files.readString(source));
            assertThat(matcher.find()).as("package declaration in %s", source).isTrue();
            Path expectedDirectory = sourceRoot.resolve(matcher.group(1).replace('.', File.separatorChar));
            assertThat(source.getParent())
                .as("package directory for %s", source)
                .isEqualTo(expectedDirectory);
        }
    }
}
