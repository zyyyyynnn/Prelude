package com.interview.identity.application.port;

import java.util.List;

public interface LegacyAvatarMigrationPort {

    List<LegacyAvatarCandidate> findLegacyAvatarBatch(int limit);

    int replaceLegacyAvatarUrl(Long userId, String expected, String replacement);

    final class LegacyAvatarCandidate {

        private Long userId;
        private String avatarUrl;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }
}
