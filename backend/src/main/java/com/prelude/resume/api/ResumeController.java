package com.prelude.resume.api;

import com.prelude.BusinessException;
import com.prelude.Result;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.resume.application.DeleteResume;
import com.prelude.resume.application.ImportResumePdf;
import com.prelude.resume.application.ImportResumeResult;
import com.prelude.resume.application.ListResumes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ListResumes listResumes;
    private final DeleteResume deleteResume;
    private final CurrentAccount currentAccount;

    @PostMapping("/upload")
    public Result<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long accountId = currentAccountId();
        try {
            ImportResumeResult result = importResumePdf.execute(accountId, file.getOriginalFilename(), file.getBytes());
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

    @GetMapping("/list")
    public Result<List<ResumeItemResponse>> list() {
        List<ResumeItemResponse> response = listResumes.execute(currentAccountId()).stream()
            .map(item -> new ResumeItemResponse(
                item.id(), item.fileName(), item.createdAt(), item.sessionCount(), item.inUse()
            ))
            .toList();
        return Result.success(response);
    }

    @DeleteMapping("/{resumeId}")
    public Result<Void> delete(@PathVariable Long resumeId) {
        deleteResume.execute(currentAccountId(), resumeId);
        return Result.success();
    }

    private Long currentAccountId() {
        return currentAccount.requireId();
    }

}
