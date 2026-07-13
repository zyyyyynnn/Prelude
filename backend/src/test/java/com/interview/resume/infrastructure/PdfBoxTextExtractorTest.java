package com.interview.resume.infrastructure;

import com.interview.shared.api.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxTextExtractorTest {

    private final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

    @Test
    void extractsTextFromValidPdf() throws Exception {
        assertThat(extractor.extract(pdfWithText("Java backend"))).contains("Java backend");
    }

    @Test
    void rejectsInvalidPdfBytes() {
        assertThatThrownBy(() -> extractor.extract("not-pdf".getBytes()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("PDF");
    }

    @Test
    void rejectsPdfWithoutText() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);

            assertThatThrownBy(() -> extractor.extract(output.toByteArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未提取到有效文本");
        }
    }

    private byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
