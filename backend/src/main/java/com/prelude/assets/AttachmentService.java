package com.prelude.assets;

import com.prelude.BusinessException;
import com.prelude.assets.api.AssetRef;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.application.port.AssetLookup;
import com.prelude.assets.application.port.AssetLookup.AssetRow;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.assets.application.port.AttachmentStorage.AttachmentRow;
import com.prelude.documents.api.DocumentContent;
import com.prelude.documents.api.DocumentExtractor;
import com.prelude.identity.api.CurrentAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interview-context attachments. Binary content lives only in the referenced
 * asset (object storage); this service owns the attachment metadata and the
 * business binding semantics.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService implements AttachmentContextPort {

    private static final String KIND_ATTACHMENT = "attachment";
    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ATTACHMENT_COUNT = 5;
    private static final int MAX_EXTRACTED_TEXT = 100_000;

    private final AttachmentStorage attachmentStorage;
    private final AssetLookup assetLookup;
    private final AssetService assetService;
    private final AttachmentPublication attachmentPublication;
    private final ObjectStoragePort objectStoragePort;
    private final DocumentExtractor documentExtractor;
    private final CurrentAccount currentAccount;

    public AttachmentSnapshot upload(String originalName, String mediaType, byte[] bytes) {
        long accountId = currentAccount.requireId();
        String fileName = safeFileName(originalName);
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("附件不能为空");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw BusinessException.badRequest("附件大小不能超过 10MB");
        }
        String resolvedMediaType = mediaType == null || mediaType.isBlank()
            ? "application/octet-stream" : mediaType;
        DocumentContent extracted = documentExtractor.extract(fileName, resolvedMediaType, bytes);

        AssetRow asset = assetService.createPending(accountId, KIND_ATTACHMENT, resolvedMediaType, bytes.length);
        try {
            objectStoragePort.put(asset.objectKey(), resolvedMediaType, bytes);
        } catch (RuntimeException exception) {
            // The PENDING row stays as the recovery anchor; the reconciler reclaims it.
            throw BusinessException.badRequest("附件上传失败");
        }

        AttachmentRow stored = new AttachmentRow(
            null, accountId, asset.id(), fileName,
            truncate(extracted.text(), MAX_EXTRACTED_TEXT), null, null);
        // One DB transaction: business reference + PENDING_UPLOAD → READY.
        // A failure rolls both back and leaves the asset PENDING for the reconciler.
        try {
            stored = attachmentPublication.finalizeUpload(asset, stored);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("附件上传失败");
        }

        return toSnapshot(stored, assetService.requireOwnedReady(accountId, asset.id()));
    }

    /**
     * Deterministic deletion: validate ownership, delete the remote object
     * (failure leaves both rows intact), then remove the asset row — the
     * attachment metadata goes with it through the FK cascade in the same
     * statement.
     */
    public void deleteUnbound(Long attachmentId) {
        long accountId = currentAccount.requireId();
        AttachmentRow stored = attachmentStorage.findUnboundOwned(accountId, attachmentId);
        if (stored == null) {
            throw BusinessException.badRequest("附件不存在、已被使用或无权删除");
        }
        AssetRow asset = assetLookup.findById(stored.assetId());
        if (asset == null) {
            attachmentStorage.deleteById(stored.id());
            return;
        }
        objectStoragePort.delete(asset.objectKey());
        assetLookup.deleteById(asset.id());
    }

    @Override
    public List<AttachmentSnapshot> requireOwned(Long accountId, List<Long> attachmentIds) {
        List<Long> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) return List.of();
        List<AttachmentRow> rows = attachmentStorage.findUnboundOwned(accountId, ids);
        if (rows.size() != ids.size()) {
            throw BusinessException.badRequest("附件不存在、已使用或无权访问");
        }
        Map<Long, AttachmentRow> byId = rows.stream()
            .collect(Collectors.toMap(AttachmentRow::id, Function.identity()));
        return toSnapshots(ids.stream().map(byId::get).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long accountId, List<Long> attachmentIds, String scopeType, Long scopeId) {
        List<Long> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) return;
        requireOwned(accountId, ids);
        int updated = attachmentStorage.bindToScope(accountId, ids, scopeType, scopeId);
        if (updated != ids.size()) {
            throw BusinessException.badRequest("附件绑定失败，请重新上传");
        }
    }

    @Override
    public List<AttachmentSnapshot> list(Long accountId, String scopeType, Long scopeId) {
        return toSnapshots(attachmentStorage.listByScope(accountId, scopeType, scopeId));
    }

    @Override
    public void unbind(Long accountId, String scopeType, Long scopeId) {
        attachmentStorage.unbindScope(accountId, scopeType, scopeId);
    }

    @Override
    public byte[] readOwnedContent(Long accountId, AssetRef assetRef) {
        AssetRow asset = assetService.requireOwnedReady(accountId, assetRef.id());
        if (!KIND_ATTACHMENT.equals(asset.kind())) {
            throw BusinessException.notFound("资产不存在");
        }
        return assetService.readContent(asset);
    }

    private List<Long> normalizeIds(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return List.of();
        List<Long> ids = new LinkedHashSet<>(attachmentIds).stream().filter(java.util.Objects::nonNull).toList();
        if (ids.size() != attachmentIds.size()) {
            throw BusinessException.badRequest("附件列表包含重复或无效项");
        }
        if (ids.size() > MAX_ATTACHMENT_COUNT) {
            throw BusinessException.badRequest("每场面试最多添加 5 个附件");
        }
        return ids;
    }

    private List<AttachmentSnapshot> toSnapshots(List<AttachmentRow> storedRows) {
        if (storedRows.isEmpty()) {
            return List.of();
        }
        List<Long> assetIds = storedRows.stream()
            .map(AttachmentRow::assetId)
            .distinct()
            .toList();
        Map<Long, AssetRow> assetsById = assetLookup.findByIds(assetIds).stream()
            .collect(Collectors.toMap(AssetRow::id, Function.identity()));
        return storedRows.stream()
            .map(stored -> {
                AssetRow asset = assetsById.get(stored.assetId());
                if (asset == null) {
                    throw BusinessException.notFound("素材不存在");
                }
                return toSnapshot(stored, asset);
            })
            .toList();
    }

    private AttachmentSnapshot toSnapshot(AttachmentRow stored, AssetRow asset) {
        return new AttachmentSnapshot(
            stored.id(),
            stored.fileName(),
            asset.mediaType(),
            asset.byteSize() == null ? 0L : asset.byteSize(),
            asset.mediaType() != null && asset.mediaType().startsWith("image/"),
            stored.extractedText(),
            new AssetRef(asset.id())
        );
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "attachment";
        try {
            String fileName = Path.of(originalName).getFileName().toString().trim();
            return fileName.isBlank() ? "attachment" : fileName;
        } catch (InvalidPathException exception) {
            throw BusinessException.badRequest("文件名无效");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        int end = maxLength;
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end);
    }
}
