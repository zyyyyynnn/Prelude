package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.documents.api.DocumentExtractor;
import com.prelude.resume.application.port.ResumeParser;
import com.prelude.resume.application.port.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportResumePdf {

    private static final int MAX_LLM_PARSE_TEXT_LENGTH = 12_000;
    private static final int MAX_RAW_TEXT_STORE_LENGTH = 100_000;

    private final DocumentExtractor documentExtractor;
    private final ResumeParser resumeParser;
    private final ResumeRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public ImportResumeResult execute(Long accountId, String fileName, byte[] pdfBytes) {
        validate(accountId, fileName, pdfBytes);
        String extracted = documentExtractor.extract(fileName, "application/pdf", pdfBytes).text();
        String rawText = truncate(extracted, MAX_RAW_TEXT_STORE_LENGTH);
        ResumeParser.ParsedResume parsed = resumeParser.parse(
            accountId, truncate(extracted, MAX_LLM_PARSE_TEXT_LENGTH)
        );
        List<ResumeRepository.ParsedProject> projects = parsed.projects().stream()
            .map(project -> new ResumeRepository.ParsedProject(project.name(), project.description()))
            .toList();
        ResumeRepository.StoredResume stored = repository.create(new ResumeRepository.NewResume(
            accountId, fileName, rawText, parsed.skills(), projects
        ));
        return new ImportResumeResult(stored.id(), parsed.skills(), parsed.projects());
    }

    private void validate(Long accountId, String fileName, byte[] bytes) {
        if (accountId == null) {
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
