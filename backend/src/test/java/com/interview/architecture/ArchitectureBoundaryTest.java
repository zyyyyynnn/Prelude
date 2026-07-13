package com.interview.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureBoundaryTest {

    @Test
    void domainPackagesHaveNoSpringDependencies() throws IOException {
        Path root = backendSourceRoot().resolve(Path.of("com", "interview"));
        try (var files = Files.walk(root)) {
            for (Path file : files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> path.toString().contains("domain" + java.io.File.separator))
                .toList()) {
                assertThat(Files.readString(file, StandardCharsets.UTF_8))
                    .as(file.toString())
                    .doesNotContain("org.springframework")
                    .doesNotContain("@Component")
                    .doesNotContain("@Service");
            }
        }
    }

    @Test
    void domainApplicationsDoNotImportOwnInfrastructure() throws IOException {
        Path root = backendSourceRoot().resolve(Path.of("com", "interview"));
        for (String domain : List.of("resume", "interview", "insight")) {
            Path application = root.resolve(Path.of(domain, "application"));
            String sources = readJavaTree(application);
            assertThat(sources)
                .as(domain + " application")
                .doesNotContain("com.interview." + domain + ".infrastructure");
        }
    }

    @Test
    void interviewApplicationDoesNotImportItsApiAdapter() throws IOException {
        String sources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "interview", "application"))
        );

        assertThat(sources).doesNotContain("com.interview.interview.api");
    }

    @Test
    void coreApplicationsDoNotDependOnMybatis() throws IOException {
        Path root = backendSourceRoot().resolve(Path.of("com", "interview"));
        for (String domain : List.of("resume", "interview", "insight")) {
            String sources = readJavaTree(root.resolve(Path.of(domain, "application")));
            assertThat(sources)
                .as(domain + " application")
                .doesNotContain("com.baomidou.mybatisplus")
                .doesNotContain("com.interview.mapper");
        }
    }

    @Test
    void coreApplicationsDoNotImportBootstrap() throws IOException {
        Path root = backendSourceRoot().resolve(Path.of("com", "interview"));
        for (String domain : List.of("resume", "interview", "insight")) {
            String sources = readJavaTree(root.resolve(Path.of(domain, "application")));
            assertThat(sources)
                .as(domain + " application")
                .doesNotContain("com.interview.bootstrap");
        }
    }

    @Test
    void apiAdaptersDoNotImportPersistenceMappers() throws IOException {
        Path root = backendSourceRoot().resolve(Path.of("com", "interview"));
        for (String domain : List.of("identity", "resume", "interview", "insight", "catalog")) {
            String sources = readJavaTree(root.resolve(Path.of(domain, "api")));
            assertThat(sources)
                .as(domain + " api")
                .doesNotContain(".infrastructure.persistence");
        }
    }

    @Test
    void productionCodeUsesTargetRootPackages() throws IOException {
        String sources = readJavaTree(backendSourceRoot().resolve(Path.of("com", "interview")));
        for (String legacy : List.of(
            "common", "config", "controller", "dto", "entity", "llm", "mapper",
            "messaging", "security", "service", "util"
        )) {
            assertThat(sources)
                .as("legacy root package " + legacy)
                .doesNotContain("package com.interview." + legacy);
        }
    }

    @Test
    void legacyInterviewFacadeIsRemoved() {
        assertThat(backendSourceRoot().resolve(Path.of(
            "com", "interview", "service", "impl", "InterviewServiceImpl.java"
        ))).doesNotExist();
        assertThat(backendSourceRoot().resolve(Path.of(
            "com", "interview", "service", "InterviewService.java"
        ))).doesNotExist();
    }

    @Test
    void interviewApiOnlyDelegatesApplicationUseCases() throws IOException {
        String source = readBackendSource(
            "com", "interview", "interview", "api", "InterviewController.java"
        );

        assertThat(source)
            .doesNotContain(".infrastructure.persistence")
            .doesNotContain("SseEmitterRegistry")
            .doesNotContain("RabbitTemplate")
            .doesNotContain("UserContext")
            .contains("private final StartInterview startInterview;")
            .contains("private final StreamChatTurn streamChatTurn;")
            .contains("private final FinishInterview finishInterview;");
        assertThat(source.lines().count()).isLessThan(100);
    }

    @Test
    void interviewApplicationDoesNotImportResumeInfrastructure() throws IOException {
        Path applicationRoot = backendSourceRoot()
            .resolve(Path.of("com", "interview", "interview", "application"));

        try (var files = Files.walk(applicationRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file, StandardCharsets.UTF_8))
                    .as(file.toString())
                    .doesNotContain("com.interview.resume.infrastructure.persistence.ResumeMapper")
                    .doesNotContain("com.interview.resume.infrastructure");
            }
        }
    }

    @Test
    void interviewApplicationUsesCatalogPort() throws IOException {
        String sources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "interview", "application"))
        );

        assertThat(sources)
            .doesNotContain("PositionTemplateMapper")
            .doesNotContain("com.interview.catalog.infrastructure")
            .contains("PositionCatalogPort");
    }

    @Test
    void resumeInfrastructureUsesInterviewUsagePort() throws IOException {
        String sources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "resume", "infrastructure"))
        );

        assertThat(sources)
            .doesNotContain("InterviewSessionMapper")
            .doesNotContain("com.interview.interview.domain.InterviewSession")
            .contains("ResumeUsagePort");
    }

    @Test
    void interviewApplicationUsesRetrievalPortInsteadOfLegacyRagService() throws IOException {
        Path applicationRoot = backendSourceRoot()
            .resolve(Path.of("com", "interview", "interview", "application"));

        try (var files = Files.walk(applicationRoot)) {
            String applicationSources = files
                .filter(path -> path.toString().endsWith(".java"))
                .map(ArchitectureBoundaryTest::readUnchecked)
                .reduce("", (left, right) -> left + right);

            assertThat(applicationSources)
                .doesNotContain("com.interview.service.SessionRagService")
                .contains("com.interview.platform.retrieval.RetrievalPort");
        }
    }

    @Test
    void applicationAndRetrievalDependOnLlmPorts() throws IOException {
        String interviewSources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "interview", "application"))
        );
        String retrievalSources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "platform", "retrieval"))
        );

        assertThat(interviewSources)
            .doesNotContain("com.interview.platform.llm.LlmRouter")
            .contains("com.interview.platform.llm.ChatPort")
            .contains("com.interview.platform.llm.LlmConfigPort");
        assertThat(retrievalSources)
            .doesNotContain("com.interview.service.EmbeddingService")
            .contains("com.interview.platform.llm.EmbedPort");
    }

    @Test
    void reportWorkerOnlyAdaptsRabbitDeliveryToInsightHandler() throws IOException {
        String source = readBackendSource(
            "com", "interview", "insight", "infrastructure", "ReportJobWorker.java"
        );

        assertThat(source)
            .contains("private final ReportGenerateHandler reportGenerateHandler;")
            .doesNotContain("com.interview.mapper")
            .doesNotContain("ChatPort")
            .doesNotContain("ObjectMapper");
        assertThat(source.lines().count()).isLessThan(30);
    }

    @Test
    void insightApplicationUsesInterviewReportPort() throws IOException {
        String sources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "insight", "application"))
        );

        assertThat(sources)
            .doesNotContain("InterviewSessionMapper")
            .doesNotContain("InterviewMessageMapper")
            .doesNotContain("InterviewStageMapper")
            .contains("InterviewReportPort");
    }

    @Test
    void finishInterviewUsesJobPortInsteadOfRabbitClient() throws IOException {
        String source = readBackendSource(
            "com", "interview", "interview", "application", "FinishInterview.java"
        );

        assertThat(source)
            .contains("JobSchedulerPort")
            .doesNotContain("RabbitTemplate")
            .doesNotContain("RabbitMqConfig");
    }

    @Test
    void textAndVoiceAdaptersShareTheSameTurnUseCase() throws IOException {
        String textAdapter = readBackendSource(
            "com", "interview", "interview", "application", "StreamChatTurn.java"
        );
        String voiceAdapter = readBackendSource(
            "com", "interview", "interview", "api", "voice", "VoiceInterviewTurnService.java"
        );

        assertThat(textAdapter)
            .contains("RunInterviewTurn")
            .doesNotContain("ChatPort")
            .doesNotContain("InterviewMessageMapper")
            .doesNotContain("InterviewStageManager");
        assertThat(voiceAdapter)
            .contains("RunInterviewTurn")
            .doesNotContain("ChatPort")
            .doesNotContain("InterviewMessageService")
            .doesNotContain("InterviewStageManager")
            .doesNotContain("InterviewContextService");
    }

    @Test
    void voiceAdapterUsesLlmAccessPort() throws IOException {
        String source = readBackendSource(
            "com", "interview", "interview", "infrastructure", "voice", "VoiceServiceImpl.java"
        );

        assertThat(source)
            .doesNotContain("UserMapper")
            .doesNotContain("LlmProviderConfigMapper")
            .doesNotContain("AesGcmEncryptor")
            .contains("VoiceModelAccessPort");
    }

    @Test
    void realtimePublishingDependsOnPortInsteadOfLegacyRegistry() throws IOException {
        String interviewSources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "interview"))
        );
        String insightSources = readJavaTree(
            backendSourceRoot().resolve(Path.of("com", "interview", "insight"))
        );

        assertThat(interviewSources + insightSources)
            .doesNotContain("SseEmitterRegistry")
            .contains("RealtimePort");
    }

    private static String readBackendSource(String... path) throws IOException {
        return Files.readString(backendSourceRoot().resolve(Path.of("", path)), StandardCharsets.UTF_8);
    }

    private static Path backendSourceRoot() {
        Path root = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
        Path sourceRoot = root.resolve(Path.of("src", "main", "java"));
        if (!Files.isDirectory(sourceRoot)) {
            sourceRoot = root.resolve("backend").resolve(Path.of("src", "main", "java"));
        }
        return sourceRoot;
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readJavaTree(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files
                .filter(path -> path.toString().endsWith(".java"))
                .map(ArchitectureBoundaryTest::readUnchecked)
                .reduce("", (left, right) -> left + right);
        }
    }
}
