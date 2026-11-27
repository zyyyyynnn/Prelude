package com.prelude.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.web.ResumeController;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DomainSingleSourceOfTruthTest {

    private static final Set<String> FORBIDDEN_TYPE_NAMES = Set.of(
        "Workspace", "ProductArea", "InterviewRun", "ResumeWorkspace", "ResumeTemplate"
    );

    private static final List<Pattern> FORBIDDEN_TABLES = List.of(
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?workspace(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?product_area(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?interview_run(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?resume_workspace(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?resume_template(?:_[a-z0-9_]+)?`?\\b")
    );

    @Test
    void forbiddenDomainTypesDoNotExist() {
        Set<String> typeNames = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.prelude")
            .stream()
            .map(JavaClass::getSimpleName)
            .collect(Collectors.toSet());

        assertThat(typeNames).noneMatch(name -> FORBIDDEN_TYPE_NAMES.contains(name)
            || name.startsWith("Workspace")
            || name.startsWith("ProductArea")
            || name.startsWith("InterviewRun")
            || name.startsWith("ResumeWorkspace")
            || name.startsWith("ResumeTemplate"));
    }

    @Test
    void forbiddenTablesDoNotExistInMigrations() throws IOException {
        Path migrationRoot = Path.of("src/main/resources/db/migration");
        try (Stream<Path> files = Files.walk(migrationRoot)) {
            List<Path> migrations = files.filter(path -> path.toString().endsWith(".sql")).toList();
            assertThat(migrations).isNotEmpty();
            for (Path migration : migrations) {
                String sql = Files.readString(migration);
                for (Pattern table : FORBIDDEN_TABLES) {
                    assertThat(table.matcher(sql).find())
                        .as("forbidden table in %s", migration)
                        .isFalse();
                }
            }
        }
    }

    @Test
    void resumeHttpSurfaceOnlyImportsListsAndDeletes() {
        Set<String> publicMethods = Arrays.stream(ResumeController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(method -> method.getName())
            .collect(Collectors.toSet());

        assertThat(publicMethods).containsExactlyInAnyOrder("upload", "list", "delete");
    }

    @Test
    void resumeRepositoryOnlyCreatesReadsAndDeletes() {
        Set<String> methods = Arrays.stream(ResumeRepository.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(method -> method.getName())
            .collect(Collectors.toSet());

        assertThat(methods).containsExactlyInAnyOrder(
            "create", "findById", "listByOwner", "hasInterviewSessions", "delete");
    }
}
