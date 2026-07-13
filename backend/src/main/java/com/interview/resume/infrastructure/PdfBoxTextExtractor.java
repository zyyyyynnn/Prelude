package com.interview.resume.infrastructure;

import com.interview.shared.api.BusinessException;
import com.interview.resume.application.port.PdfTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfBoxTextExtractor implements PdfTextExtractor {

    private static final int MAX_PDF_PAGES = 50;

    @Override
    public String extract(byte[] pdfBytes) {
        validateMagic(pdfBytes);
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw BusinessException.badRequest("PDF 页数超过上限（50 页），请精简后重试");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw BusinessException.badRequest("PDF 未提取到有效文本，请确认不是纯图片扫描件");
            }
            return text.trim();
        } catch (IOException exception) {
            throw BusinessException.badRequest("PDF 文本提取失败，请检查文件格式");
        }
    }

    private void validateMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 4
            || bytes[0] != 0x25 || bytes[1] != 0x50 || bytes[2] != 0x44 || bytes[3] != 0x46) {
            throw BusinessException.badRequest("文件头不是 PDF 格式，请检查上传文件");
        }
    }
}
