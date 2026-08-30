package com.prelude.assets.api;

import com.prelude.Result;
import com.prelude.assets.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping
    public Result<AttachmentResponse> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return Result.success(AttachmentResponse.from(attachmentService.upload(
            file.getOriginalFilename(), file.getContentType(), file.getBytes()
        )));
    }

    @DeleteMapping("/{attachmentId}")
    public Result<Void> delete(@PathVariable Long attachmentId) {
        attachmentService.deleteUnbound(attachmentId);
        return Result.success(null);
    }
}
