package com.interview.identity.api;

import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/media/avatars")
@RequiredArgsConstructor
public class AvatarMediaController {

    private final AvatarStoragePort avatarStoragePort;

    @GetMapping("/{objectKey:.+}")
    public ResponseEntity<InputStreamResource> getAvatar(@PathVariable String objectKey) {
        AvatarStoragePort.StoredResource resource = avatarStoragePort.open(objectKey)
            .orElseThrow(() -> BusinessException.notFound("头像资源不存在"));
        MediaType contentType = MediaType.parseMediaType(resource.contentType());
        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(resource.contentLength())
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(resource.objectKey()).build().toString()
            )
            .body(new InputStreamResource(resource.content()));
    }
}
