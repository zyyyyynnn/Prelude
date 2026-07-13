package com.interview.resume.application;

import com.interview.shared.api.BusinessException;
import com.interview.resume.application.port.PdfTextExtractor;
import com.interview.resume.application.port.ResumeParser;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.domain.ResumeDocumentFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportResumePdf {

    private static final int MAX_LLM_PARSE_TEXT_LENGTH = 12_000;
    private static final int MAX_RAW_TEXT_STORE_LENGTH = 100_000;

    private final PdfTextExtractor pdfTextExtractor;
    private final ResumeParser resumeParser;
    private final ResumeRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public ImportResumeResult execute(Long userId, String fileName, byte[] pdfBytes) {
        validate(userId, fileName, pdfBytes);
        String extracted = pdfTextExtractor.extract(pdfBytes);
        String rawText = truncate(extracted, MAX_RAW_TEXT_STORE_LENGTH);
        ResumeParser.ParsedResume parsed = resumeParser.parse(
            userId, truncate(extracted, MAX_LLM_PARSE_TEXT_LENGTH)
        );
        List<ResumeDocumentFactory.ImportedProject> projects = parsed.projects().stream()
            .map(project -> new ResumeDocumentFactory.ImportedProject(project.name(), project.description()))
            .toList();
        ResumeDocument document = ResumeDocumentFactory.fromImport(rawText, parsed.skills(), projects);
        ResumeRepository.StoredResume stored = repository.create(new ResumeRepository.NewResume(
            userId, fileName, rawText, document, "pdf_import"
        ));
        return new ImportResumeResult(stored.id(), parsed.skills(), parsed.projects());
    }

    private void validate(Long userId, String fileName, byte[] bytes) {
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw BusinessException.badRequest("仅支持 PDF 文件");
        }
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("请上传 PDF 简历文件");
        }
        if (bytes.length > 10 * 1024 * 1024) {
            throw BusinessException.badRequest("文件大小不能超过 10MB");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end);
    }
}
