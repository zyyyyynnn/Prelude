package com.prelude.assets.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.prelude.assets.domain.AssetDeletionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_deletion_request")
public class AssetDeletionRequestEntity {

    private Long id;
    private Long accountId;
    private Long assetId;
    private String objectKey;
    private AssetDeletionStatus status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime claimedAt;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
