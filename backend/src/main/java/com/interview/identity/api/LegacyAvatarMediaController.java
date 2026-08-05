package com.interview.identity.api;

import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/uploads/avatars")
@RequiredArgsConstructor
public class LegacyAvatarMediaController {

    private final LegacyAvatarSourcePort legacyAvatarSource;

    @GetMapping("/{objectKey:.+}")
    public ResponseEntity<InputStreamResource> getLegacyAvatar(@PathVariable String objectKey) {
        LegacyAvatarSourcePort.ReadResult result = legacyAvatarSource.read(objectKey);
        if (result.status() != LegacyAvatarSourcePort.Status.SUPPORTED
            && result.status() != LegacyAvatarSourcePort.Status.UNSUPPORTED_WEBP) {
            throw BusinessException.notFound("历史头像资源不存在");
        }
        LegacyAvatarSourcePort.LegacyAvatarResource resource = result.resource();
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(resource.contentType()))
            .contentLength(resource.contentLength())
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .header("X-Content-Type-Options", "nosniff")
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(resource.objectKey()).build().toString()
            )
            .body(new InputStreamResource(resource.content()));
    }
}
