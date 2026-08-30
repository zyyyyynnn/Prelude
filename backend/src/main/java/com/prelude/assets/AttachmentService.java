package com.prelude.assets;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.BusinessException;
import com.prelude.assets.api.AssetRef;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.domain.AssetStatus;
import com.prelude.assets.persistence.Asset;
import com.prelude.assets.persistence.AssetMapper;
import com.prelude.assets.persistence.AttachmentMapper;
import com.prelude.assets.persistence.StoredAttachment;
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

    private final AttachmentMapper attachmentMapper;
    private final AssetMapper assetMapper;
    private final AssetService assetService;
    private final AttachmentPublication attachmentPublication;
    private final ObjectStoragePort objectStoragePort;
    private final DocumentExtractor documentExtractor;
    private final CurrentAccount currentAccount;

    public AttachmentSnapshot upload(String originalName, String mediaType, byte[] bytes) {
        long accountId = currentAccount.requireId();
        String fileName = safeFileName(originalName);
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("请选择附件");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw BusinessException.badRequest("单个附件不能超过 10MB");
        }
        String resolvedMediaType = mediaType == null || mediaType.isBlank()
            ? "application/octet-stream" : mediaType;
        DocumentContent extracted = documentExtractor.extract(fileName, resolvedMediaType, bytes);

        Asset asset = assetService.createPending(accountId, KIND_ATTACHMENT, resolvedMediaType, bytes.length);
        try {
            objectStoragePort.put(asset.getObjectKey(), resolvedMediaType, bytes);
        } catch (RuntimeException exception) {
            // The PENDING row stays as the recovery anchor; the reconciler reclaims it.
            throw BusinessException.badRequest("附件上传失败");
        }

        StoredAttachment stored = new StoredAttachment();
        stored.setAccountId(accountId);
        stored.setAssetId(asset.getId());
        stored.setFileName(fileName);
        stored.setExtractedText(truncate(extracted.text(), MAX_EXTRACTED_TEXT));
        // One DB transaction: business reference + PENDING_UPLOAD → READY.
        // A failure rolls both back and leaves the asset PENDING for the reconciler.
        try {
            attachmentPublication.finalizeUpload(asset, stored);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("附件上传失败");
        }

        return toSnapshot(stored, assetService.requireOwnedReady(accountId, asset.getId()));
    }

    /**
     * Deterministic deletion: validate ownership, delete the remote object
     * (failure leaves both rows intact), then remove the asset row — the
     * attachment metadata goes with it through the FK cascade in the same
     * statement.
     */
    public void deleteUnbound(Long attachmentId) {
        long accountId = currentAccount.requireId();
        StoredAttachment stored = attachmentMapper.selectOne(new LambdaQueryWrapper<StoredAttachment>()
            .eq(StoredAttachment::getId, attachmentId)
            .eq(StoredAttachment::getAccountId, accountId)
            .isNull(StoredAttachment::getScopeType)
            .last("LIMIT 1"));
        if (stored == null) {
            throw BusinessException.badRequest("附件不存在、已使用或无权删除");
        }
        Asset asset = assetMapper.selectById(stored.getAssetId());
        if (asset == null) {
            attachmentMapper.deleteById(stored.getId());
            return;
        }
        objectStoragePort.delete(asset.getObjectKey());
        assetMapper.deleteById(asset.getId());
    }

    @Override
    public List<AttachmentSnapshot> requireOwned(Long accountId, List<Long> attachmentIds) {
        List<Long> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) return List.of();
        List<StoredAttachment> rows = attachmentMapper.selectList(
            new LambdaQueryWrapper<StoredAttachment>()
                .in(StoredAttachment::getId, ids)
                .eq(StoredAttachment::getAccountId, accountId)
                .isNull(StoredAttachment::getScopeType)
        );
        if (rows.size() != ids.size()) {
            throw BusinessException.badRequest("附件不存在、已使用或无权访问");
        }
        return ids.stream()
            .map(id -> rows.stream().filter(row -> id.equals(row.getId())).findFirst().orElseThrow())
            .map(this::toSnapshot)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long accountId, List<Long> attachmentIds, String scopeType, Long scopeId) {
        List<Long> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) return;
        requireOwned(accountId, ids);
        int updated = attachmentMapper.update(null, new LambdaUpdateWrapper<StoredAttachment>()
            .set(StoredAttachment::getScopeType, scopeType)
            .set(StoredAttachment::getScopeId, scopeId)
            .in(StoredAttachment::getId, ids)
            .eq(StoredAttachment::getAccountId, accountId)
            .isNull(StoredAttachment::getScopeType));
        if (updated != ids.size()) {
            throw BusinessException.badRequest("附件绑定失败，请重新上传");
        }
    }

    @Override
    public List<AttachmentSnapshot> list(Long accountId, String scopeType, Long scopeId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<StoredAttachment>()
                .eq(StoredAttachment::getAccountId, accountId)
                .eq(StoredAttachment::getScopeType, scopeType)
                .eq(StoredAttachment::getScopeId, scopeId)
                .orderByAsc(StoredAttachment::getId))
            .stream()
            .map(this::toSnapshot)
            .toList();
    }

    @Override
    public byte[] readOwnedContent(Long accountId, AssetRef assetRef) {
        Asset asset = assetService.requireOwnedReady(accountId, assetRef.id());
        if (!KIND_ATTACHMENT.equals(asset.getKind())) {
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

    private AttachmentSnapshot toSnapshot(StoredAttachment stored) {
        Asset asset = assetMapper.selectById(stored.getAssetId());
        if (asset == null) {
            throw BusinessException.notFound("资产不存在");
        }
        return toSnapshot(stored, asset);
    }

    private AttachmentSnapshot toSnapshot(StoredAttachment stored, Asset asset) {
        return new AttachmentSnapshot(
            stored.getId(),
            stored.getFileName(),
            asset.getMediaType(),
            asset.getByteSize() == null ? 0L : asset.getByteSize(),
            asset.getMediaType() != null && asset.getMediaType().startsWith("image/"),
            stored.getExtractedText(),
            new AssetRef(asset.getId())
        );
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "attachment";
        try {
            String fileName = Path.of(originalName).getFileName().toString().trim();
            return fileName.isBlank() ? "attachment" : fileName;
        } catch (InvalidPathException exception) {
            throw BusinessException.badRequest("附件名称无效");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        int end = maxLength;
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end);
    }
}
