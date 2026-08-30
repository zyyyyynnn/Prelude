package com.prelude.assets.api;

import java.util.List;

public interface AttachmentContextPort {

    List<AttachmentSnapshot> requireOwned(Long userId, List<Long> attachmentIds);

    void bind(Long userId, List<Long> attachmentIds, String scopeType, Long scopeId);

    List<AttachmentSnapshot> list(Long userId, String scopeType, Long scopeId);
}
