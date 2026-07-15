package com.interview.resume.api;

import com.interview.shared.api.BusinessException;
import com.interview.shared.api.Result;
import com.interview.shared.web.UserContext;
import com.interview.resume.api.ResumeItemResponse;
import com.interview.resume.api.ResumeProjectDto;
import com.interview.resume.api.ResumeUploadResponse;
import com.interview.resume.application.CreateResumeDocument;
import com.interview.resume.application.DeleteResume;
import com.interview.resume.application.GetResumeDocument;
import com.interview.resume.application.ImportResumePdf;
import com.interview.resume.application.ImportResumeResult;
import com.interview.resume.application.ListResumes;
import com.interview.resume.application.ResumeDocumentView;
import com.interview.resume.application.ResumeImprovementDecisionView;
import com.interview.resume.application.ResumeImprovementService;
import com.interview.resume.application.ResumeImprovementView;
import com.interview.resume.application.UpdateResumeDocument;
import com.interview.resume.application.port.ResumeFixturePort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ImportResumePdf importResumePdf;
    private final CreateResumeDocument createResumeDocument;
    private final GetResumeDocument getResumeDocument;
    private final UpdateResumeDocument updateResumeDocument;
    private final ListResumes listResumes;
    private final DeleteResume deleteResume;
    private final ResumeImprovementService resumeImprovementService;
    private final ResumeFixturePort devFixtureService;

    @PostMapping("/upload")
    public Result<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = currentUserId();
        if (devFixtureService.isEnabled()) {
            validateFixtureFile(file);
            return Result.success(toUploadResponse(
                devFixtureService.createDevFixtureResume(userId, file.getOriginalFilename())
            ));
        }
        try {
            ImportResumeResult result = importResumePdf.execute(userId, file.getOriginalFilename(), file.getBytes());
            return Result.success(toUploadResponse(result));
        } catch (IOException exception) {
            throw BusinessException.badRequest("文件读取失败");
        }
    }

    private ResumeUploadResponse toUploadResponse(ImportResumeResult result) {
        List<ResumeProjectDto> projects = result.projects().stream().map(project -> {
                ResumeProjectDto dto = new ResumeProjectDto();
                dto.setName(project.name());
                dto.setDescription(project.description());
                return dto;
            }).toList();
        return new ResumeUploadResponse(result.resumeId(), result.skills(), projects);
    }

    @PostMapping("/document")
    public Result<ResumeDocumentView> create(@RequestBody CreateResumeDocumentRequest request) {
        return Result.success(createResumeDocument.execute(
            currentUserId(), request.fileName(), request.document()
        ));
    }

    @GetMapping("/{resumeId}/document")
    public Result<ResumeDocumentView> getDocument(@PathVariable Long resumeId) {
        return Result.success(getResumeDocument.execute(currentUserId(), resumeId));
    }

    @PutMapping("/{resumeId}/document")
    public Result<ResumeDocumentView> update(
        @PathVariable Long resumeId,
        @RequestBody UpdateResumeDocumentRequest request
    ) {
        return Result.success(updateResumeDocument.execute(
            currentUserId(), resumeId, request.expectedVersion(), request.document()
        ));
    }

    @GetMapping("/list")
    public Result<List<ResumeItemResponse>> list() {
        List<ResumeItemResponse> response = listResumes.execute(currentUserId()).stream()
            .map(item -> new ResumeItemResponse(
                item.id(), item.fileName(), item.createdAt(), item.sessionCount(), item.inUse()
            ))
            .toList();
        return Result.success(response);
    }

    @DeleteMapping("/{resumeId}")
    public Result<Void> delete(@PathVariable Long resumeId) {
        deleteResume.execute(currentUserId(), resumeId);
        return Result.success();
    }

    @GetMapping("/{resumeId}/improvements")
    public Result<List<ResumeImprovementView>> listImprovements(
        @PathVariable Long resumeId,
        @RequestParam(value = "sessionId", required = false) Long sessionId
    ) {
        return Result.success(resumeImprovementService.list(currentUserId(), resumeId, sessionId));
    }

    @PostMapping("/improvements/{improvementId}/accept")
    public Result<ResumeImprovementDecisionView> acceptImprovement(@PathVariable Long improvementId) {
        return Result.success(resumeImprovementService.accept(currentUserId(), improvementId));
    }

    @PostMapping("/improvements/{improvementId}/reject")
    public Result<ResumeImprovementView> rejectImprovement(@PathVariable Long improvementId) {
        return Result.success(resumeImprovementService.reject(currentUserId(), improvementId));
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }

    private void validateFixtureFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传 PDF 简历文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw BusinessException.badRequest("仅支持 PDF 文件");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw BusinessException.badRequest("文件大小不能超过 10MB");
        }
    }
}
