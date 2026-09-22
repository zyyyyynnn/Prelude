package com.prelude.assets.application.port;

import java.util.List;

/**
 * Attachment metadata access. The service owns the binding semantics; this port owns
 * the rows. It speaks in the attachment's own fields rather than the persistence row
 * type, so no use case names the persistence package.
 */
public interface AttachmentStorage {

    /** The account's unbound attachment, or null when there is none. */
    AttachmentRow findUnboundOwned(Long accountId, Long attachmentId);

    /** Every owned, still-unbound attachment among {@code attachmentIds}, in id order. */
    List<AttachmentRow> findUnboundOwned(Long accountId, List<Long> attachmentIds);

    List<AttachmentRow> listByScope(Long accountId, String scopeType, Long scopeId);

    /**
     * Claims the given attachments for a scope. Returns the number of rows actually
     * claimed, so a concurrent binder that lost the race is detected rather than assumed.
     */
    int bindToScope(Long accountId, List<Long> attachmentIds, String scopeType, Long scopeId);

    void unbindScope(Long accountId, String scopeType, Long scopeId);

    /** Persists a new attachment and returns the stored row with its identifier. */
    AttachmentRow insert(AttachmentRow stored);

    void deleteById(Long attachmentId);

    /** The attachment row as the service sees it. */
    record AttachmentRow(
        Long id,
        Long accountId,
        Long assetId,
        String fileName,
        String extractedText,
        String scopeType,
        Long scopeId
    ) {
    }
}
