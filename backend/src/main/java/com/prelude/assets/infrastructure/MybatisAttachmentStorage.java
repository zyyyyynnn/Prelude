package com.prelude.assets.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.assets.application.port.AttachmentStorage;
import com.prelude.assets.application.port.AttachmentStorage.AttachmentRow;
import com.prelude.assets.infrastructure.persistence.AttachmentMapper;
import com.prelude.assets.infrastructure.persistence.StoredAttachmentEntity;
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
        StoredAttachmentEntity row = attachmentMapper.selectOne(new LambdaQueryWrapper<StoredAttachmentEntity>()
            .eq(StoredAttachmentEntity::getId, attachmentId)
            .eq(StoredAttachmentEntity::getAccountId, accountId)
            .isNull(StoredAttachmentEntity::getScopeType)
            .last("LIMIT 1"));
        return row == null ? null : toRow(row);
    }

    @Override
    public List<AttachmentRow> findUnboundOwned(Long accountId, List<Long> attachmentIds) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<StoredAttachmentEntity>()
                .in(StoredAttachmentEntity::getId, attachmentIds)
                .eq(StoredAttachmentEntity::getAccountId, accountId)
                .isNull(StoredAttachmentEntity::getScopeType))
            .stream()
            .map(this::toRow)
            .toList();
    }

    @Override
    public List<AttachmentRow> listByScope(Long accountId, String scopeType, Long scopeId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<StoredAttachmentEntity>()
                .eq(StoredAttachmentEntity::getAccountId, accountId)
                .eq(StoredAttachmentEntity::getScopeType, scopeType)
                .eq(StoredAttachmentEntity::getScopeId, scopeId)
                .orderByAsc(StoredAttachmentEntity::getId))
            .stream()
            .map(this::toRow)
            .toList();
    }

    @Override
    public int bindToScope(Long accountId, List<Long> attachmentIds, String scopeType, Long scopeId) {
        return attachmentMapper.update(null, new LambdaUpdateWrapper<StoredAttachmentEntity>()
            .set(StoredAttachmentEntity::getScopeType, scopeType)
            .set(StoredAttachmentEntity::getScopeId, scopeId)
            .in(StoredAttachmentEntity::getId, attachmentIds)
            .eq(StoredAttachmentEntity::getAccountId, accountId)
            .isNull(StoredAttachmentEntity::getScopeType));
    }

    @Override
    public void unbindScope(Long accountId, String scopeType, Long scopeId) {
        attachmentMapper.delete(new LambdaQueryWrapper<StoredAttachmentEntity>()
            .eq(StoredAttachmentEntity::getAccountId, accountId)
            .eq(StoredAttachmentEntity::getScopeType, scopeType)
            .eq(StoredAttachmentEntity::getScopeId, scopeId));
    }

    @Override
    public AttachmentRow insert(AttachmentRow stored) {
        StoredAttachmentEntity row = new StoredAttachmentEntity();
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

    private AttachmentRow toRow(StoredAttachmentEntity row) {
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
