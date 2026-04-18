package com.interview.service;

import com.interview.dto.ResumeItemResponse;
import com.interview.dto.ResumeUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {

    ResumeUploadResponse upload(MultipartFile file);

    List<ResumeItemResponse> listCurrentUserResumes();
}
