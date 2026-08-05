package com.interview.identity.application.port;

import java.io.IOException;
import java.io.InputStream;

public interface LegacyAvatarSourcePort {

    ReadResult read(String objectKey);

    void delete(String objectKey);

    enum Status {
        SUPPORTED,
        MISSING,
        INVALID,
        UNSUPPORTED_WEBP
    }

    record ReadResult(Status status, LegacyAvatarResource resource) {

        public static ReadResult missing() {
            return new ReadResult(Status.MISSING, null);
        }

        public static ReadResult invalid() {
            return new ReadResult(Status.INVALID, null);
        }

        public static ReadResult unsupportedWebp(LegacyAvatarResource resource) {
            return new ReadResult(Status.UNSUPPORTED_WEBP, resource);
        }

        public static ReadResult supported(LegacyAvatarResource resource) {
            return new ReadResult(Status.SUPPORTED, resource);
        }
    }

    record LegacyAvatarResource(
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
