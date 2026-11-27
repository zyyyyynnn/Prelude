package com.prelude.documents;

import com.prelude.BusinessException;
import com.prelude.documents.api.DocumentContent;
import com.prelude.documents.api.DocumentExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Slf4j
@Component
class DocumentExtractorImpl implements DocumentExtractor {

    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final int MAX_CONCURRENT_PARSERS = 4;
    private static final int MAX_CHILD_ERROR_BYTES = 8192;
    private static final Duration PROCESS_TERMINATION_GRACE = Duration.ofSeconds(5);
    private static final Duration PARSE_TIMEOUT = Duration.ofSeconds(10);

    private final Duration parseTimeout;
    private final ParserProcessFactory processFactory;
    private final Semaphore parserSlots = new Semaphore(MAX_CONCURRENT_PARSERS);

    @Autowired
    DocumentExtractorImpl() {
        this(PARSE_TIMEOUT, DocumentExtractorImpl::startParserProcess);
    }

    DocumentExtractorImpl(Duration parseTimeout, ParserProcessFactory processFactory) {
        if (parseTimeout == null || parseTimeout.isZero() || parseTimeout.isNegative()) {
            throw new IllegalArgumentException("parseTimeout must be positive");
        }
        if (processFactory == null) {
            throw new IllegalArgumentException("processFactory must not be null");
        }
        this.parseTimeout = parseTimeout;
        this.processFactory = processFactory;
    }

    @Override
    public DocumentContent extract(String fileName, String mediaType, byte[] content) {
        if (content == null || content.length == 0) {
            throw BusinessException.badRequest("附件内容不能为空");
        }
        if (content.length > MAX_DOCUMENT_BYTES) {
            throw BusinessException.badRequest("附件大小不能超过 10MB");
        }
        ContentType declaredType = canonicalMediaType(mediaType);
        ContentType detectedType = detectByMagic(content);
        if (detectedType != null) {
            if (declaredType != null && declaredType != detectedType) {
                throw BusinessException.badRequest("文件内容与声明的 MIME 类型不一致");
            }
            return switch (detectedType) {
                case PDF -> new DocumentContent(DocumentContent.Kind.TEXT,
                    extractInSubprocess("pdf", content));
                case DOCX -> new DocumentContent(DocumentContent.Kind.TEXT,
                    extractInSubprocess("docx", content));
                case PNG, JPEG, WEBP -> validateImage(content);
                default -> throw unsupportedType();
            };
        }
        if (declaredType == ContentType.TEXT || declaredType == ContentType.MARKDOWN) {
            return new DocumentContent(DocumentContent.Kind.TEXT, decodeUtf8(content));
        }
        if (declaredType != null) {
            throw BusinessException.badRequest("文件内容与声明的 MIME 类型不一致");
        }
        throw unsupportedType();
    }

    private String extractInSubprocess(String format, byte[] content) {
        if (!parserSlots.tryAcquire()) {
            throw new BusinessException(503, "document_parser_busy", "文档解析服务繁忙，请稍后再试");
        }
        Path input = null;
        Path output = null;
        Path error = null;
        Process process = null;
        try {
            input = Files.createTempFile("prelude-document-input-", ".tmp");
            Files.write(input, content);
            output = Files.createTempFile("prelude-document-output-", ".tmp");
            error = Files.createTempFile("prelude-document-error-", ".tmp");
            process = processFactory.start(format, input, output, error);
            if (!process.waitFor(parseTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                terminate(process);
                throw new BusinessException(504, "document_parse_timeout", "文档解析超时，请精简后重试");
            }
            if (process.exitValue() == 0) {
                return readExtractedText(output);
            }
            if (process.exitValue() == 2) {
                throw BusinessException.badRequest(readChildError(error));
            }
            log.warn("Document parser exited with code {}: {}", process.exitValue(), readChildDiagnostic(error));
            throw BusinessException.badRequest("文档解析失败，请检查文件格式");
        } catch (InterruptedException exception) {
            Thread.interrupted();
            if (process != null) {
                terminate(process);
            }
            Thread.currentThread().interrupt();
            throw new BusinessException(504, "document_parse_interrupted", "文档解析已中断，请重试");
        } catch (IOException exception) {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            throw BusinessException.badRequest("文档解析失败，请检查文件格式");
        } finally {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            deleteTemporaryFiles(input, output, error);
            parserSlots.release();
        }
    }

    private String readExtractedText(Path output) throws IOException {
        if (Files.size(output) > DocumentParserProcess.MAX_TEXT_BYTES) {
            throw new BusinessException(400, "document_result_too_large", "文档解析结果超过上限");
        }
        String text = Files.readString(output, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw BusinessException.badRequest("文档未提取到有效文本");
        }
        return text.trim();
    }

    private String readChildError(Path error) throws IOException {
        if (Files.size(error) > MAX_CHILD_ERROR_BYTES) {
            return "文档解析失败，请检查文件格式";
        }
        try (InputStream input = Files.newInputStream(error)) {
            byte[] bytes = input.readNBytes(MAX_CHILD_ERROR_BYTES + 1);
            if (bytes.length > MAX_CHILD_ERROR_BYTES) {
                return "文档解析失败，请检查文件格式";
            }
            String value = new String(bytes, StandardCharsets.UTF_8);
            return value.lines()
                .filter(line -> line.startsWith(DocumentParserProcess.ERROR_PREFIX))
                .map(line -> line.substring(DocumentParserProcess.ERROR_PREFIX.length()).trim())
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("文档解析失败，请检查文件格式");
        }
    }

    private String readChildDiagnostic(Path error) throws IOException {
        if (!Files.exists(error) || Files.size(error) == 0) {
            return "";
        }
        try (InputStream input = Files.newInputStream(error)) {
            byte[] bytes = input.readNBytes(MAX_CHILD_ERROR_BYTES);
            return new String(bytes, StandardCharsets.UTF_8).lines().findFirst().orElse("");
        }
    }

    private void terminate(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor(PROCESS_TERMINATION_GRACE.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteTemporaryFiles(Path... files) {
        for (Path file : files) {
            if (file == null) {
                continue;
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                log.warn("Could not remove document parser temporary file", exception);
            }
        }
    }

    private static Process startParserProcess(String format, Path input, Path output, Path error) throws IOException {
        return new ProcessBuilder(parserCommand(DocumentParserProcess.class, format, input, output, error))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(error.toFile())
            .start();
    }

    static List<String> parserCommand(Class<?> parserClass, String format, Path input, Path output, Path error)
        throws IOException {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        if (!Files.isExecutable(java)) {
            java = ProcessHandle.current().info().command().map(Path::of).orElse(java);
        }
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Xmx256m");
        command.add("-XX:MaxMetaspaceSize=256m");

        Path executableJar = executableSpringBootJar(parserClass);
        if (executableJar != null) {
            command.add("-Dloader.main=" + DocumentParserProcess.class.getName());
            command.add("-cp");
            command.add(executableJar.toString());
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else {
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(DocumentParserProcess.class.getName());
        }
        command.add(format);
        command.add(input.toString());
        command.add(output.toString());
        command.add(error.toString());
        return List.copyOf(command);
    }

    private static Path executableSpringBootJar(Class<?> parserClass) throws IOException {
        CodeSource codeSource = parserClass.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return null;
        }
        URL location = codeSource.getLocation();
        if ("jar".equals(location.getProtocol())) {
            String external = location.toExternalForm();
            int separator = external.indexOf("!/");
            if (separator < 0) {
                return null;
            }
            location = URI.create(external.substring(4, separator)).toURL();
        }
        if (!"file".equals(location.getProtocol())) {
            return null;
        }
        Path candidate;
        try {
            candidate = Path.of(location.toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid document parser code source", exception);
        }
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        try (JarFile jar = new JarFile(candidate.toFile())) {
            Manifest manifest = jar.getManifest();
            String mainClass = manifest == null
                ? null
                : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (!"org.springframework.boot.loader.launch.JarLauncher".equals(mainClass)
                || jar.getJarEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class") == null
                || jar.getJarEntry("BOOT-INF/classes/"
                    + parserClass.getName().replace('.', '/') + ".class") == null) {
                return null;
            }
        }
        return candidate;
    }

    private String decodeUtf8(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
            return requireText(text, "文本附件内容不能为空");
        } catch (CharacterCodingException exception) {
            throw BusinessException.badRequest("文本附件必须使用 UTF-8 编码");
        }
    }

    private DocumentContent validateImage(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw BusinessException.badRequest("无法识别图片附件");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_IMAGE_PIXELS) {
                    throw BusinessException.badRequest("图片尺寸超过限制");
                }
            } finally {
                reader.dispose();
            }
            return new DocumentContent(DocumentContent.Kind.IMAGE, null);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw BusinessException.badRequest("图片附件读取失败");
        }
    }

    private ContentType detectByMagic(byte[] content) {
        if (startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return ContentType.PDF;
        }
        if (startsWith(content, new byte[] {0x50, 0x4B, 0x03, 0x04})) {
            return ContentType.DOCX;
        }
        if (startsWith(content, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ContentType.PNG;
        }
        if (startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return ContentType.JPEG;
        }
        if (content.length >= 12
            && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return ContentType.WEBP;
        }
        return null;
    }

    private ContentType canonicalMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return null;
        }
        int parameterStart = mediaType.indexOf(';');
        String type = mediaType.substring(0, parameterStart < 0 ? mediaType.length() : parameterStart)
            .trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "application/pdf", "application/x-pdf" -> ContentType.PDF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ContentType.DOCX;
            case "text/plain" -> ContentType.TEXT;
            case "text/markdown", "text/x-markdown" -> ContentType.MARKDOWN;
            case "image/png" -> ContentType.PNG;
            case "image/jpeg", "image/jpg", "image/pjpeg" -> ContentType.JPEG;
            case "image/webp" -> ContentType.WEBP;
            default -> null;
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException unsupportedType() {
        return BusinessException.badRequest("仅支持 PDF、DOCX、TXT、Markdown、PNG、JPG 和 WebP 附件");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(message);
        }
        return value.trim();
    }

    @FunctionalInterface
    interface ParserProcessFactory {
        Process start(String format, Path input, Path output, Path error) throws IOException;
    }

    private enum ContentType {
        PDF,
        DOCX,
        TEXT,
        MARKDOWN,
        PNG,
        JPEG,
        WEBP
    }
}
