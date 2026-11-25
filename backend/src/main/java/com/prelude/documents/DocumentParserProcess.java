package com.prelude.documents;

import com.prelude.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class DocumentParserProcess {

    static final int MAX_TEXT_BYTES = 4 * 1024 * 1024;
    static final String ERROR_PREFIX = "document-error:";
    private static final int MAX_PDF_PAGES = 50;
    private static final int MAX_DOCX_ENTRIES = 256;
    private static final long MAX_DOCX_ENTRY_BYTES = 16L * 1024 * 1024;
    private static final long MAX_DOCX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final long MAX_DOCX_COMPRESSION_RATIO = 200;

    private DocumentParserProcess() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.exit(2);
            return;
        }
        Path output = Path.of(args[2]);
        Path error = Path.of(args[3]);
        try {
            String text = switch (args[0]) {
                case "pdf" -> extractPdf(Path.of(args[1]));
                case "docx" -> extractDocx(Path.of(args[1]));
                default -> throw new IllegalArgumentException("Unsupported document format");
            };
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_TEXT_BYTES) {
                throw new BusinessException(400, "document_result_too_large", "文档解析结果超过上限");
            }
            Files.write(output, bytes);
        } catch (BusinessException exception) {
            fail(error, exception.getMessage());
        } catch (Exception exception) {
            fail(error, switch (args[0]) {
                case "pdf" -> "PDF 文本提取失败，请检查文件格式";
                case "docx" -> "DOCX 文本提取失败，请检查文件格式";
                default -> "文档解析失败，请检查文件格式";
            });
        }
    }

    private static String extractPdf(Path input) throws IOException {
        try (PDDocument document = Loader.loadPDF(input.toFile())) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new BusinessException(400, "bad_request", "PDF 页数超过上限（50 页），请精简后重试");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return requireText(stripper.getText(document), "PDF 未提取到有效文本，请确认不是纯图片扫描件");
        }
    }

    private static String extractDocx(Path input) throws IOException {
        byte[] content = Files.readAllBytes(input);
        validateDocxArchive(content);
        try (InputStream source = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(source);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return requireText(extractor.getText(), "DOCX 未提取到有效文本");
        }
    }

    private static void validateDocxArchive(byte[] content) throws IOException {
        boolean hasContentTypes = false;
        boolean hasRelationships = false;
        boolean hasDocument = false;
        int entryCount = 0;
        long totalExpanded = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_DOCX_ENTRIES) {
                    throw new BusinessException(400, "bad_request", "DOCX 条目数超过上限（256 个）");
                }
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                hasRelationships |= "_rels/.rels".equals(entry.getName());
                hasDocument |= "word/document.xml".equals(entry.getName());
                if (entry.getSize() > MAX_DOCX_ENTRY_BYTES) {
                    throw new BusinessException(400, "bad_request", "DOCX 单条目展开大小超过上限（16MB）");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                long expanded = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    totalExpanded += read;
                    if (expanded > MAX_DOCX_ENTRY_BYTES) {
                        throw new BusinessException(400, "bad_request", "DOCX 单条目展开大小超过上限（16MB）");
                    }
                    if (totalExpanded > MAX_DOCX_TOTAL_BYTES) {
                        throw new BusinessException(400, "bad_request", "DOCX 总展开大小超过上限（64MB）");
                    }
                }
            }
        }
        if (!hasContentTypes || !hasRelationships || !hasDocument) {
            throw new BusinessException(400, "bad_request", "DOCX 结构不完整，请检查文件格式");
        }
        if (totalExpanded > (long) content.length * MAX_DOCX_COMPRESSION_RATIO) {
            throw new BusinessException(400, "bad_request", "DOCX 压缩比超过上限（200:1）");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "bad_request", message);
        }
        return value.trim();
    }

    private static void fail(Path error, String message) throws IOException {
        Files.writeString(error, ERROR_PREFIX + message, StandardCharsets.UTF_8);
        System.exit(2);
    }
}
