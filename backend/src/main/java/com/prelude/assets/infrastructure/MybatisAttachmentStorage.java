package com.prelude.assets.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.assets.application.port.AttachmentStorage.AttachmentRow;
import com.prelude.assets.infrastructure.persistence.AttachmentMapper;
import com.prelude.assets.infrastructure.persistence.StoredAttachment;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for {@link AttachmentStorage}; the only place that names the mapper. */
@Repository
@RequiredArgsConstructor
public class MybatisAttachmentStorage implements AttachmentStorage {

    private final AttachmentMapper attachmentMapper;

    @Override
    public AttachmentRow findUnboundOwned(Long accountId, Long attachmentId) {
        StoredAttachment row = attachmentMapper.selectOne(new LambdaQueryWrapper<StoredAttachment>()
            .eq(StoredAttachment::getId, attachmentId)
            .eq(StoredAttachment::getAccountId, accountId)
            .isNull(StoredAttachment::getScopeType)
            .last("LIMIT 1"));
        return row == null ? null : toRow(row);
    }

    @Override
    public List<AttachmentRow> findUnboundOwned(Long accountId, List<Long> attachmentIds) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<StoredAttachment>()
                .in(StoredAttachment::getId, attachmentIds)
                .eq(StoredAttachment::getAccountId, accountId)
                .isNull(StoredAttachment::getScopeType))
            .stream()
            .map(this::toRow)
            .toList();
    }

    @Override
    public List<AttachmentRow> listByScope(Long accountId, String scopeType, Long scopeId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<StoredAttachment>()
                .eq(StoredAttachment::getAccountId, accountId)
                .eq(StoredAttachment::getScopeType, scopeType)
                .eq(StoredAttachment::getScopeId, scopeId)
                .orderByAsc(StoredAttachment::getId))
            .stream()
            .map(this::toRow)
            .toList();
    }

    @Override
    public int bindToScope(Long accountId, List<Long> attachmentIds, String scopeType, Long scopeId) {
        return attachmentMapper.update(null, new LambdaUpdateWrapper<StoredAttachment>()
            .set(StoredAttachment::getScopeType, scopeType)
            .set(StoredAttachment::getScopeId, scopeId)
            .in(StoredAttachment::getId, attachmentIds)
            .eq(StoredAttachment::getAccountId, accountId)
            .isNull(StoredAttachment::getScopeType));
    }

    @Override
    public void unbindScope(Long accountId, String scopeType, Long scopeId) {
        attachmentMapper.delete(new LambdaQueryWrapper<StoredAttachment>()
            .eq(StoredAttachment::getAccountId, accountId)
            .eq(StoredAttachment::getScopeType, scopeType)
            .eq(StoredAttachment::getScopeId, scopeId));
    }

    @Override
    public AttachmentRow insert(AttachmentRow stored) {
        StoredAttachment row = new StoredAttachment();
        row.setAccountId(stored.accountId());
        row.setAssetId(stored.assetId());
        row.setFileName(stored.fileName());
        row.setExtractedText(stored.extractedText());
        row.setScopeType(stored.scopeType());
        row.setScopeId(stored.scopeId());
        attachmentMapper.insert(row);
        return toRow(row);
    }

    @Override
    public void deleteById(Long attachmentId) {
        attachmentMapper.deleteById(attachmentId);
    }

    private AttachmentRow toRow(StoredAttachment row) {
        return new AttachmentRow(
            row.getId(),
            row.getAccountId(),
            row.getAssetId(),
            row.getFileName(),
            row.getExtractedText(),
            row.getScopeType(),
            row.getScopeId()
        );
    }
}
