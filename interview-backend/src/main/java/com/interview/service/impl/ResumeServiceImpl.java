package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.ResumeItemResponse;
import com.interview.dto.ResumeParseResult;
import com.interview.dto.ResumeUploadResponse;
import com.interview.entity.Resume;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final ResumeMapper resumeMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ObjectMapper objectMapper;
    private final LlmRouter llmRouter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse upload(MultipartFile file) {
        validateFile(file);
        String rawText = extractPdfText(file);
        ResumeParseResult parseResult = parseByLlm(rawText);

        Resume resume = new Resume();
        resume.setUserId(currentUserId());
        resume.setFileName(file.getOriginalFilename());
        resume.setRawText(rawText);
        try {
            resume.setParsedSkills(objectMapper.writeValueAsString(parseResult.getSkills()));
            resume.setParsedProjects(objectMapper.writeValueAsString(parseResult.getProjects()));
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest("简历解析结果序列化失败");
        }
        resumeMapper.insert(resume);

        return new ResumeUploadResponse(resume.getId(), parseResult.getSkills(), parseResult.getProjects());
    }

    @Override
    public List<ResumeItemResponse> listCurrentUserResumes() {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, currentUserId())
                .orderByDesc(Resume::getCreatedAt))
            .stream()
            .map(resume -> {
                long sessionCount = interviewSessionMapper.selectCount(new LambdaQueryWrapper<InterviewSession>()
                    .eq(InterviewSession::getResumeId, resume.getId()));
                return new ResumeItemResponse(
                    resume.getId(),
                    resume.getFileName(),
                    resume.getCreatedAt(),
                    sessionCount,
                    sessionCount > 0
                );
            })
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentUserResume(Long resumeId) {
        Long userId = currentUserId();
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw BusinessException.badRequest("简历不存在或无权访问");
        }

        long sessionCount = interviewSessionMapper.selectCount(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getResumeId, resumeId));
        if (sessionCount > 0) {
            throw BusinessException.badRequest("该简历已被面试使用，无法删除");
        }

        resumeMapper.deleteById(resumeId);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传 PDF 简历文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw BusinessException.badRequest("仅支持 PDF 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("文件大小不能超过 10MB");
        }
    }

    private String extractPdfText(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
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

    private ResumeParseResult parseByLlm(String rawText) {
        String systemPrompt = """
            你是简历解析助手。请只输出严格 JSON，不要输出 Markdown，不要解释。
            JSON 格式必须为：{"skills":["技能1"],"projects":[{"name":"项目名","description":"项目描述"}]}
            """;
        String userPrompt = "请从以下中文简历文本中提取技能列表和项目经历：\n" + rawText;
        String content = stripJsonFence(llmRouter.chatCurrentUser(List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        )));
        try {
            return objectMapper.readValue(content, ResumeParseResult.class);
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest("LLM 返回内容不是合法 JSON，请重试");
        }
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }
}
