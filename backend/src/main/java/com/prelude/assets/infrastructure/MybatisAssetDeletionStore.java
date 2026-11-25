package com.prelude.assets.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.assets.application.port.AssetDeletionStore;
import com.prelude.assets.application.port.AssetDeletionStore.DeletionRequest;
import com.prelude.assets.domain.AssetDeletionStatus;
import com.prelude.assets.infrastructure.persistence.AssetDeletionRequestEntity;
import com.prelude.assets.infrastructure.persistence.AssetDeletionRequestMapper;
import com.prelude.assets.infrastructure.persistence.AssetEntity;
import com.prelude.assets.infrastructure.persistence.AssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class MybatisAssetDeletionStore implements AssetDeletionStore {

    private static final String LEASE_EXPIRED = "WORKER_LEASE_EXPIRED";

    private final AssetDeletionRequestMapper deletionRequestMapper;
    private final AssetMapper assetMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long accountId, Long assetId, String objectKey) {
        AssetDeletionRequestEntity existing = findByAssetId(assetId);
        if (existing != null) {
            requireSameRegistration(existing, accountId, objectKey);
            return;
        }
        AssetDeletionRequestEntity request = new AssetDeletionRequestEntity();
        request.setAccountId(accountId);
        request.setAssetId(assetId);
        request.setObjectKey(objectKey);
        request.setStatus(AssetDeletionStatus.PENDING);
        request.setAttemptCount(0);
        try {
            deletionRequestMapper.insert(request);
        } catch (DuplicateKeyException exception) {
            requireSameRegistration(findByAssetIdForUpdate(assetId), accountId, objectKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeletionRequest> claimDue(int limit, LocalDateTime now, Duration leaseDuration) {
        List<AssetDeletionRequestEntity> expired = deletionRequestMapper.selectList(
            new LambdaQueryWrapper<AssetDeletionRequestEntity>()
                .eq(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.RUNNING)
                .le(AssetDeletionRequestEntity::getLeaseExpiresAt, now)
                .orderByAsc(AssetDeletionRequestEntity::getLeaseExpiresAt)
                .orderByAsc(AssetDeletionRequestEntity::getId)
                .last("LIMIT " + limit + " FOR UPDATE"));
        for (AssetDeletionRequestEntity request : expired) {
            deletionRequestMapper.update(null, new LambdaUpdateWrapper<AssetDeletionRequestEntity>()
                .set(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.PENDING)
                // Database timestamp rounding must not push recovered work past this pass.
                .set(AssetDeletionRequestEntity::getNextAttemptAt, now.minusSeconds(1))
                .set(AssetDeletionRequestEntity::getLastError, LEASE_EXPIRED)
                .set(AssetDeletionRequestEntity::getClaimedAt, null)
                .set(AssetDeletionRequestEntity::getLeaseExpiresAt, null)
                .eq(AssetDeletionRequestEntity::getId, request.getId())
                .eq(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.RUNNING)
                .le(AssetDeletionRequestEntity::getLeaseExpiresAt, now));
        }

        List<AssetDeletionRequestEntity> due = deletionRequestMapper.selectList(
            new LambdaQueryWrapper<AssetDeletionRequestEntity>()
                .eq(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.PENDING)
                .le(AssetDeletionRequestEntity::getNextAttemptAt, now)
                .orderByAsc(AssetDeletionRequestEntity::getNextAttemptAt)
                .orderByAsc(AssetDeletionRequestEntity::getId)
                .last("LIMIT " + limit + " FOR UPDATE"));
        List<DeletionRequest> claimed = new ArrayList<>(due.size());
        for (AssetDeletionRequestEntity request : due) {
            int nextAttemptCount = request.getAttemptCount() + 1;
            int updated = deletionRequestMapper.update(null, new LambdaUpdateWrapper<AssetDeletionRequestEntity>()
                .set(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.RUNNING)
                .set(AssetDeletionRequestEntity::getAttemptCount, nextAttemptCount)
                .set(AssetDeletionRequestEntity::getClaimedAt, now)
                .set(AssetDeletionRequestEntity::getLeaseExpiresAt, now.plus(leaseDuration))
                .set(AssetDeletionRequestEntity::getLastError, null)
                .eq(AssetDeletionRequestEntity::getId, request.getId())
                .eq(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.PENDING)
                .eq(AssetDeletionRequestEntity::getAttemptCount, request.getAttemptCount())
                .le(AssetDeletionRequestEntity::getNextAttemptAt, now));
            if (updated == 1) {
                claimed.add(toRequest(request, nextAttemptCount));
            }
        }
        return claimed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(DeletionRequest request) {
        assetMapper.delete(new LambdaQueryWrapper<AssetEntity>()
            .eq(AssetEntity::getId, request.assetId())
            .eq(AssetEntity::getAccountId, request.accountId()));
        int removed = deletionRequestMapper.delete(new LambdaQueryWrapper<AssetDeletionRequestEntity>()
            .eq(AssetDeletionRequestEntity::getId, request.id())
            .eq(AssetDeletionRequestEntity::getAccountId, request.accountId())
            .eq(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.RUNNING)
            .eq(AssetDeletionRequestEntity::getAttemptCount, request.attemptCount()));
        if (removed != 1) {
            throw new IllegalStateException("Asset deletion request lease is no longer current");
        }
    }

    @Override
    public void retry(DeletionRequest request, LocalDateTime nextAttemptAt, String failure) {
        deletionRequestMapper.update(null, currentAttemptUpdate(request)
            .set(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.PENDING)
            .set(AssetDeletionRequestEntity::getNextAttemptAt, nextAttemptAt)
            .set(AssetDeletionRequestEntity::getLastError, failure)
            .set(AssetDeletionRequestEntity::getClaimedAt, null)
            .set(AssetDeletionRequestEntity::getLeaseExpiresAt, null));
    }

    @Override
    public void block(DeletionRequest request, String failure) {
        deletionRequestMapper.update(null, currentAttemptUpdate(request)
            .set(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.BLOCKED)
            .set(AssetDeletionRequestEntity::getLastError, failure)
            .set(AssetDeletionRequestEntity::getClaimedAt, null)
            .set(AssetDeletionRequestEntity::getLeaseExpiresAt, null));
    }

    private LambdaUpdateWrapper<AssetDeletionRequestEntity> currentAttemptUpdate(DeletionRequest request) {
        return new LambdaUpdateWrapper<AssetDeletionRequestEntity>()
            .eq(AssetDeletionRequestEntity::getId, request.id())
            .eq(AssetDeletionRequestEntity::getAccountId, request.accountId())
            .eq(AssetDeletionRequestEntity::getStatus, AssetDeletionStatus.RUNNING)
            .eq(AssetDeletionRequestEntity::getAttemptCount, request.attemptCount());
    }

    private AssetDeletionRequestEntity findByAssetId(Long assetId) {
        return deletionRequestMapper.selectOne(new LambdaQueryWrapper<AssetDeletionRequestEntity>()
            .eq(AssetDeletionRequestEntity::getAssetId, assetId)
            .last("LIMIT 1"));
    }

    private AssetDeletionRequestEntity findByAssetIdForUpdate(Long assetId) {
        return deletionRequestMapper.selectOne(new LambdaQueryWrapper<AssetDeletionRequestEntity>()
            .eq(AssetDeletionRequestEntity::getAssetId, assetId)
            .last("LIMIT 1 FOR UPDATE"));
    }

    private void requireSameRegistration(AssetDeletionRequestEntity existing, Long accountId, String objectKey) {
        if (existing == null
            || !Objects.equals(existing.getAccountId(), accountId)
            || !Objects.equals(existing.getObjectKey(), objectKey)) {
            throw new IllegalStateException("Asset deletion registration conflicts with an existing request");
        }
    }

    private DeletionRequest toRequest(AssetDeletionRequestEntity request, int attemptCount) {
        return new DeletionRequest(
            request.getId(),
            request.getAccountId(),
            request.getAssetId(),
            request.getObjectKey(),
            attemptCount
        );
    }
}
