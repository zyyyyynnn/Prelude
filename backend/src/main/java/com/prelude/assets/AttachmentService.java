package com.prelude.assets;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.BusinessException;
import com.prelude.UserContext;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.assets.persistence.AttachmentMapper;
import com.prelude.assets.persistence.StoredAttachment;
import com.prelude.documents.api.DocumentContent;
import com.prelude.documents.api.DocumentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachmentService implements AttachmentContextPort {

    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ATTACHMENT_COUNT = 5;
    private static final int MAX_EXTRACTED_TEXT = 100_000;

    private final AttachmentMapper attachmentMapper;
    private final DocumentExtractor documentExtractor;

    @Transactional(rollbackFor = Exception.class)
    public AttachmentSnapshot upload(String originalName, String mediaType, byte[] bytes) {
        Long userId = currentUserId();
        String fileName = safeFileName(originalName);
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("请选择附件");
        }
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw BusinessException.badRequest("单个附件不能超过 10MB");
        }
        DocumentContent extracted = documentExtractor.extract(fileName, mediaType, bytes);
        StoredAttachment stored = new StoredAttachment();
        stored.setUserId(userId);
        stored.setFileName(fileName);
        stored.setMediaType(mediaType == null || mediaType.isBlank()
            ? "application/octet-stream" : mediaType);
        stored.setByteSize((long) bytes.length);
        stored.setImage(extracted.kind() == DocumentContent.Kind.IMAGE ? 1 : 0);
        stored.setExtractedText(truncate(extracted.text(), MAX_EXTRACTED_TEXT));
        stored.setContent(bytes);
        attachmentMapper.insert(stored);
        return toSnapshot(stored);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUnbound(Long attachmentId) {
        Long userId = currentUserId();
        int deleted = attachmentMapper.delete(new LambdaQueryWrapper<StoredAttachment>()
            .eq(StoredAttachment::getId, attachmentId)
            .eq(StoredAttachment::getUserId, userId)
            .isNull(StoredAttachment::getScopeType));
        if (deleted != 1) {
            throw BusinessException.badRequest("附件不存在、已使用或无权删除");
        }
    }

    @Override
    public List<AttachmentSnapshot> requireOwned(Long userId, List<Long> attachmentIds) {
        List<Long> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) return List.of();
        List<StoredAttachment> rows = attachmentMapper.selectList(
            new LambdaQueryWrapper<StoredAttachment>()
                .in(StoredAttachment::getId, ids)
                .eq(StoredAttachment::getUserId, userId)
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
    public void bind(Long userId, List<Long> attachmentIds, String scopeType, Long scopeId) {
        List<Long> ids = normalizeIds(attachmentIds);
        if (ids.isEmpty()) return;
        requireOwned(userId, ids);
        int updated = attachmentMapper.update(null, new LambdaUpdateWrapper<StoredAttachment>()
            .set(StoredAttachment::getScopeType, scopeType)
            .set(StoredAttachment::getScopeId, scopeId)
            .in(StoredAttachment::getId, ids)
            .eq(StoredAttachment::getUserId, userId)
            .isNull(StoredAttachment::getScopeType));
        if (updated != ids.size()) {
            throw BusinessException.badRequest("附件绑定失败，请重新上传");
        }
    }

    @Override
    public List<AttachmentSnapshot> list(Long userId, String scopeType, Long scopeId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<StoredAttachment>()
                .eq(StoredAttachment::getUserId, userId)
                .eq(StoredAttachment::getScopeType, scopeType)
                .eq(StoredAttachment::getScopeId, scopeId)
                .orderByAsc(StoredAttachment::getId))
            .stream()
            .map(this::toSnapshot)
            .toList();
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
        return new AttachmentSnapshot(
            stored.getId(), stored.getFileName(), stored.getMediaType(), stored.getByteSize(),
            Integer.valueOf(1).equals(stored.getImage()), stored.getExtractedText(), stored.getContent()
        );
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw BusinessException.unauthorized("请先登录");
        return userId;
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
