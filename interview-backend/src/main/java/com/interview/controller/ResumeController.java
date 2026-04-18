package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.ResumeItemResponse;
import com.interview.dto.ResumeUploadResponse;
import com.interview.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload")
    public Result<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(resumeService.upload(file));
    }

    @GetMapping("/list")
    public Result<List<ResumeItemResponse>> list() {
        return Result.success(resumeService.listCurrentUserResumes());
    }
}
