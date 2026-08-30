package com.prelude.assets.api;

import java.util.List;

public interface AttachmentContextPort {

    List<AttachmentSnapshot> requireOwned(Long accountId, List<Long> attachmentIds);

    void bind(Long accountId, List<Long> attachmentIds, String scopeType, Long scopeId);

    List<AttachmentSnapshot> list(Long accountId, String scopeType, Long scopeId);

    /**
     * Controlled binary read for consumers that genuinely need the bytes
     * (e.g. multimodal LLM calls). Metadata-only consumers must use snapshots.
     */
    byte[] readContent(AssetRef assetRef);
}
