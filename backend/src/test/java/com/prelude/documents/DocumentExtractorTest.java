package com.prelude.documents;

import com.prelude.BusinessException;
import com.prelude.documents.api.DocumentContent;
import com.prelude.documents.api.DocumentExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentExtractorTest {

    private DocumentExtractor extractor;

    @BeforeEach
    void createExtractor() {
        extractor = new DocumentExtractorImpl();
    }

    @Test
    void identifiesTextByCanonicalMimeInsteadOfFileExtension() {
        DocumentContent plain = extractor.extract(
            "not-a-text-file.pdf", "text/plain; charset=utf-8", "Java backend".getBytes(StandardCharsets.UTF_8));
        DocumentContent markdown = extractor.extract(
            "not-markdown.png", "text/x-markdown", "# Resume".getBytes(StandardCharsets.UTF_8));

        assertThat(plain.kind()).isEqualTo(DocumentContent.Kind.TEXT);
        assertThat(plain.text()).isEqualTo("Java backend");
        assertThat(markdown.text()).isEqualTo("# Resume");
    }

    @Test
    void identifiesPdfAndDocxByMagicWithMisleadingNamesAndGenericMime() throws IOException {
        byte[] pdf = pdf(1);
        byte[] docx = docx();

        DocumentContent extractedPdf = extractor.extract("resume.txt", "application/octet-stream", pdf);
        DocumentContent extractedDocx = extractor.extract("resume.png", "application/octet-stream", docx);

        assertThat(extractedPdf.text()).contains("Java backend");
        assertThat(extractedDocx.text()).contains("Spring platform");
    }

    @Test
    void executableSpringBootJarStartsTheParserThroughPropertiesLauncher(@TempDir Path directory) throws Exception {
        Path jar = executableSpringBootJar(directory);
        Path input = Path.of("input.pdf");
        Path output = Path.of("output.txt");
        Path error = Path.of("error.txt");

        URL classesUrl = new URL("jar:" + jar.toUri().toURL().toExternalForm() + "!/BOOT-INF/classes/");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classesUrl}, null)) {
            Class<?> parserClass = loader.loadClass(BootParserFixture.class.getName());
            List<String> command = DocumentExtractorImpl.parserCommand(parserClass, "pdf", input, output, error);

            assertThat(command).containsSequence(
                javaExecutable().toString(),
                "-Xmx256m",
                "-XX:MaxMetaspaceSize=256m",
                "-Dloader.main=" + DocumentParserProcess.class.getName(),
                "-cp",
                jar.toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher",
                "pdf",
                input.toString(),
                output.toString(),
                error.toString()
            );
        }
    }

    @Test
    void directoryClassPathStartsTheParserMainClassDirectly() throws Exception {
        List<String> command = DocumentExtractorImpl.parserCommand(
            DocumentParserProcess.class, "docx", Path.of("input.docx"), Path.of("output.txt"), Path.of("error.txt"));

        assertThat(command).containsSequence(
            javaExecutable().toString(),
            "-Xmx256m",
            "-XX:MaxMetaspaceSize=256m",
            "-cp",
            System.getProperty("java.class.path"),
            DocumentParserProcess.class.getName(),
            "docx",
            "input.docx",
            "output.txt",
            "error.txt"
        );
        assertThat(command).noneMatch(argument -> argument.startsWith("-Dloader.main="));
    }

    @Test
    void identifiesImagesByMagicAndEnforcesPixelLimit() throws IOException {
        BufferedImage small = new BufferedImage(32, 16, BufferedImage.TYPE_BYTE_GRAY);
        byte[] png = png(small);

        DocumentContent image = extractor.extract("scan.pdf", "application/octet-stream", png);

        assertThat(image.kind()).isEqualTo(DocumentContent.Kind.IMAGE);
        assertThat(image.text()).isNull();

        byte[] oversized = png(new BufferedImage(5001, 5001, BufferedImage.TYPE_BYTE_GRAY));
        assertDocumentFailure(() -> extractor.extract("scan.png", "image/png", oversized), 400, "bad_request", "图片尺寸超过限制");
    }

    @Test
    void rejectsMimeMismatchAndNonUtf8Text() throws IOException {
        byte[] pdf = pdf(1);

        assertDocumentFailure(() -> extractor.extract("resume.pdf", "image/png", pdf), 400, "bad_request", "MIME 类型不一致");
        assertDocumentFailure(
            () -> extractor.extract("resume.txt", "text/plain", new byte[] {(byte) 0xC3, (byte) 0x28}),
            400,
            "bad_request",
            "UTF-8");
        assertDocumentFailure(
            () -> extractor.extract("resume.txt", "application/pdf", "plain text".getBytes(StandardCharsets.UTF_8)),
            400,
            "bad_request",
            "MIME 类型不一致");
    }

    @Test
    void preservesPdfPageLimit() throws IOException {
        assertDocumentFailure(
            () -> extractor.extract("resume.pdf", "application/pdf", pdf(51)),
            400,
            "bad_request",
            "页数超过上限");
    }

    @Test
    void rejectsDocxWithTooManyEntries() {
        Map<String, byte[]> entries = requiredDocxEntries();
        for (int index = 0; index < 257; index++) {
            entries.put("word/media/" + index + ".xml", new byte[] {'x'});
        }

        assertDocumentFailure(
            () -> extractor.extract("resume.docx", "application/octet-stream", zip(entries)),
            400,
            "bad_request",
            "条目数超过上限");
    }

    @Test
    void rejectsDocxSingleEntryExpansionLimit() {
        Map<String, byte[]> entries = requiredDocxEntries();
        entries.put("word/document.xml", repeated('x', 16 * 1024 * 1024 + 1));

        assertDocumentFailure(
            () -> extractor.extract("resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zip(entries)),
            400,
            "bad_request",
            "单条目展开大小超过上限");
    }

    @Test
    void rejectsDocxTotalExpansionLimit() {
        Map<String, byte[]> entries = requiredDocxEntries();
        byte[] expanded = repeated('x', 13 * 1024 * 1024);
        for (int index = 0; index < 5; index++) {
            entries.put("word/media/expanded-" + index + ".xml", expanded);
        }

        assertDocumentFailure(
            () -> extractor.extract("resume.docx", "application/octet-stream", zip(entries)),
            400,
            "bad_request",
            "总展开大小超过上限");
    }

    @Test
    void rejectsDocxCompressionRatioLimit() {
        Map<String, byte[]> entries = requiredDocxEntries();
        entries.put("word/document.xml", repeated('x', 1024 * 1024));

        assertDocumentFailure(
            () -> extractor.extract("resume.docx", "application/octet-stream", zip(entries)),
            400,
            "bad_request",
            "压缩比超过上限");
    }

    @Test
    void timeoutForciblyTerminatesTheRealParserProcessAndRemovesTemporaryFiles() throws Exception {
        AtomicReference<Process> started = new AtomicReference<>();
        AtomicReference<Path> input = new AtomicReference<>();
        AtomicReference<Path> output = new AtomicReference<>();
        AtomicReference<Path> error = new AtomicReference<>();
        DocumentExtractorImpl timed = new DocumentExtractorImpl(Duration.ofSeconds(3),
            testProcessFactory(StallingParserProcess.class, started, input, output, error));

        assertDocumentFailure(
            () -> timed.extract("resume.pdf", "application/pdf", pdf(1)),
            504,
            "document_parse_timeout",
            "文档解析超时");

        assertThat(started.get()).isNotNull();
        assertThat(started.get().isAlive()).isFalse();
        assertThat(Files.exists(input.get())).isFalse();
        assertThat(Files.exists(output.get())).isFalse();
        assertThat(Files.exists(error.get())).isFalse();
    }

    @Test
    void rejectsParserOutputAboveTheByteLimit() throws Exception {
        AtomicReference<Process> started = new AtomicReference<>();
        DocumentExtractorImpl limited = new DocumentExtractorImpl(Duration.ofSeconds(5),
            testProcessFactory(OversizedOutputParserProcess.class, started, new AtomicReference<>(),
                new AtomicReference<>(), new AtomicReference<>()));

        assertDocumentFailure(
            () -> limited.extract("resume.pdf", "application/pdf", pdf(1)),
            400,
            "document_result_too_large",
            "文档解析结果超过上限");
        assertThat(started.get().isAlive()).isFalse();
    }

    private Path executableSpringBootJar(Path directory) throws IOException {
        Path jarPath = directory.resolve("parser.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
            Attributes.Name.MAIN_CLASS, "org.springframework.boot.loader.launch.JarLauncher");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            writeJarEntry(jar, "org/springframework/boot/loader/launch/PropertiesLauncher.class", new byte[0]);
            writeJarEntry(jar, "BOOT-INF/classes/com/prelude/documents/DocumentParserProcess.class", new byte[0]);
            String fixtureEntry = "BOOT-INF/classes/"
                + BootParserFixture.class.getName().replace('.', '/') + ".class";
            try (InputStream input = BootParserFixture.class.getResourceAsStream(
                "/" + BootParserFixture.class.getName().replace('.', '/') + ".class")) {
                assertThat(input).isNotNull();
                jar.putNextEntry(new JarEntry(fixtureEntry));
                input.transferTo(jar);
                jar.closeEntry();
            }
        }
        return jarPath;
    }

    private void writeJarEntry(JarOutputStream jar, String name, byte[] content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content);
        jar.closeEntry();
    }

    private Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private DocumentExtractorImpl.ParserProcessFactory testProcessFactory(
        Class<?> mainClass,
        AtomicReference<Process> started,
        AtomicReference<Path> input,
        AtomicReference<Path> output,
        AtomicReference<Path> error
    ) {
        return (format, inputPath, outputPath, errorPath) -> {
            input.set(inputPath);
            output.set(outputPath);
            error.set(errorPath);
            String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
            Path java = Path.of(System.getProperty("java.home"), "bin", executable);
            Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                mainClass.getName(),
                inputPath.toString(),
                outputPath.toString(),
                errorPath.toString()
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            started.set(process);
            return process;
        };
    }

    public static final class StallingParserProcess {
        public static void main(String[] args) throws InterruptedException {
            new CountDownLatch(1).await();
        }
    }

    public static final class OversizedOutputParserProcess {
        public static void main(String[] args) throws IOException {
            Files.write(Path.of(args[1]), new byte[DocumentParserProcess.MAX_TEXT_BYTES + 1]);
        }
    }

    private void assertDocumentFailure(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
        int status,
        String code,
        String message
    ) {
        assertThatThrownBy(callable)
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(status);
                assertThat(exception.getCode()).isEqualTo(code);
                assertThat(exception.getMessage()).contains(message);
            });
    }

    private byte[] pdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (index == 0) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72, 720);
                        content.showText("Java backend");
                        content.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docx() throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Spring platform");
            document.write(output);
            return output.toByteArray();
        }
    }

    private Map<String, byte[]> requiredDocxEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
        entries.put("_rels/.rels", "<Relationships/>".getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", "<document/>".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }

    private byte[] repeated(char value, int size) {
        byte[] content = new byte[size];
        java.util.Arrays.fill(content, (byte) value);
        return content;
    }
}

final class BootParserFixture {
}
