package com.interview.identity.api;

import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.LegacyAvatarSourcePort;
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
import java.util.Optional;

@RestController
@RequestMapping("/media/avatars")
@RequiredArgsConstructor
public class AvatarMediaController {

    private final AvatarStoragePort avatarStoragePort;
    private final LegacyAvatarSourcePort legacyAvatarSource;

    @GetMapping("/{objectKey:.+}")
    public ResponseEntity<InputStreamResource> getAvatar(@PathVariable String objectKey) {
        MediaResource resource = openMedia(objectKey);
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

    private MediaResource openMedia(String objectKey) {
        Optional<AvatarStoragePort.StoredResource> stored = avatarStoragePort.open(objectKey);
        if (stored.isPresent()) {
            AvatarStoragePort.StoredResource resource = stored.get();
            return new MediaResource(resource.objectKey(), resource.contentType(), resource.contentLength(), resource.content());
        }

        LegacyAvatarSourcePort.ReadResult legacy = legacyAvatarSource.read(objectKey);
        if (legacy.status() == LegacyAvatarSourcePort.Status.SUPPORTED
            || legacy.status() == LegacyAvatarSourcePort.Status.UNSUPPORTED_WEBP) {
            LegacyAvatarSourcePort.LegacyAvatarResource resource = legacy.resource();
            return new MediaResource(resource.objectKey(), resource.contentType(), resource.contentLength(), resource.content());
        }
        throw BusinessException.notFound("头像资源不存在");
    }

    private record MediaResource(
        String objectKey,
        String contentType,
        long contentLength,
        java.io.InputStream content
    ) {
    }
}
