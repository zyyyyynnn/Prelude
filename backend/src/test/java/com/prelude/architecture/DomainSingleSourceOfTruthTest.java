package com.prelude.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.prelude.artifact.infrastructure.persistence.ArtifactVersionEntity;
import com.prelude.artifact.infrastructure.persistence.ArtifactVersionMapper;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.infrastructure.persistence.ResumeEntity;
import com.prelude.resume.infrastructure.persistence.ResumeMapper;
import com.prelude.resume.web.ResumeController;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
        "Workspace", "ProductArea", "Membership", "InterviewRun", "InterviewResult",
        "ResumeWorkspace", "ResumeTemplate", "ResumeEditor", "ResumeRevision", "ResumePatch"
    );

    private static final List<Pattern> FORBIDDEN_TABLES = List.of(
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?workspace(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?product_area(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?membership(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?interview_run(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?interview_result(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?resume_workspace(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?resume_template(?:_[a-z0-9_]+)?`?\\b"),
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?resume_(?:editor|revision|patch)(?:_[a-z0-9_]+)?`?\\b")
    );

    @Test
    void forbiddenDomainTypesDoNotExist() {
        Set<String> typeNames = importProductionClasses().stream()
            .map(JavaClass::getSimpleName)
            .collect(Collectors.toSet());

        assertThat(typeNames).contains("InterviewSession", "ArtifactVersion");
        assertThat(typeNames).noneMatch(name -> FORBIDDEN_TYPE_NAMES.contains(name)
            || name.startsWith("Workspace")
            || name.startsWith("ProductArea")
            || name.startsWith("Membership")
            || name.startsWith("InterviewRun")
            || name.startsWith("InterviewResult")
            || name.startsWith("ResumeWorkspace")
            || name.startsWith("ResumeTemplate")
            || name.startsWith("ResumeEditor")
            || name.startsWith("ResumeRevision")
            || name.startsWith("ResumePatch"));
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
    void resumePersistenceIsConfinedToItsInfrastructure() {
        assertPersistenceAccessStaysInInfrastructure(
            Set.of(ResumeEntity.class.getName(), ResumeMapper.class.getName()),
            "com.prelude.resume.infrastructure");
    }

    @Test
    void artifactVersionPersistenceIsConfinedToItsInfrastructure() {
        assertPersistenceAccessStaysInInfrastructure(
            Set.of(ArtifactVersionEntity.class.getName(), ArtifactVersionMapper.class.getName()),
            "com.prelude.artifact.infrastructure");
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

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.prelude");
    }

    private static void assertPersistenceAccessStaysInInfrastructure(
        Set<String> persistenceTypes,
        String infrastructurePackage
    ) {
        List<String> offenders = importProductionClasses().stream()
            .filter(type -> !type.getPackageName().startsWith(infrastructurePackage))
            .filter(type -> type.getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> persistenceTypes.contains(dependency.getTargetClass().getName())))
            .map(JavaClass::getName)
            .toList();

        assertThat(offenders)
            .as("persistence access outside %s", infrastructurePackage)
            .isEmpty();
    }
}
