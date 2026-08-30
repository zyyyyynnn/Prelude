package com.prelude.documents;

import com.prelude.BusinessException;
import com.prelude.documents.api.DocumentContent;
import com.prelude.documents.api.DocumentExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
class DocumentExtractorImpl implements DocumentExtractor {

    private static final int MAX_PDF_PAGES = 50;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    @Override
    public DocumentContent extract(String fileName, String mediaType, byte[] content) {
        if (content == null || content.length == 0) {
            throw BusinessException.badRequest("附件内容不能为空");
        }
        String extension = extension(fileName);
        return switch (extension) {
            case "pdf" -> new DocumentContent(DocumentContent.Kind.TEXT, extractPdf(content));
            case "docx" -> new DocumentContent(DocumentContent.Kind.TEXT, extractDocx(content));
            case "txt", "md", "markdown" ->
                new DocumentContent(DocumentContent.Kind.TEXT, decodeUtf8(content));
            case "png", "jpg", "jpeg", "webp" -> validateImage(mediaType, content);
            default -> throw BusinessException.badRequest("仅支持 PDF、DOCX、TXT、Markdown、PNG、JPG 和 WebP 附件");
        };
    }

    private String extractPdf(byte[] content) {
        if (content.length < 4 || content[0] != 0x25 || content[1] != 0x50
            || content[2] != 0x44 || content[3] != 0x46) {
            throw BusinessException.badRequest("文件头不是 PDF 格式，请检查附件");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw BusinessException.badRequest("PDF 页数超过上限（50 页），请精简后重试");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return requireText(stripper.getText(document), "PDF 未提取到有效文本，请确认不是纯图片扫描件");
        } catch (IOException exception) {
            throw BusinessException.badRequest("PDF 文本提取失败，请检查文件格式");
        }
    }

    private String extractDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return requireText(extractor.getText(), "DOCX 未提取到有效文本");
        } catch (IOException | RuntimeException exception) {
            throw BusinessException.badRequest("DOCX 文本提取失败，请检查文件格式");
        }
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

    private DocumentContent validateImage(String mediaType, byte[] content) {
        String normalizedType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(normalizedType)) {
            throw BusinessException.badRequest("图片附件仅支持 PNG、JPG 和 WebP");
        }
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
        } catch (IOException exception) {
            throw BusinessException.badRequest("图片附件读取失败");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(message);
        }
        return value.trim();
    }

    private String extension(String fileName) {
        if (fileName == null) return "";
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
