package com.interview.identity.application.port;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface AvatarStoragePort {

    StoredAvatar store(String objectKey, ProcessedAvatar avatar);

    Optional<StoredResource> open(String objectKey);

    void delete(String objectKey);

    record StoredAvatar(String objectKey, String publicUri) {
    }

    record StoredResource(
        String objectKey,
        String contentType,
        long contentLength,
        InputStream content
    ) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
