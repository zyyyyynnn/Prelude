CREATE TABLE `asset_deletion_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '资产所属账户ID',
  `asset_id` BIGINT NOT NULL COMMENT '待删除资产ID',
  `object_key` VARCHAR(128) NOT NULL COMMENT '登记时冻结的对象键',
  `status` ENUM('PENDING','RUNNING','BLOCKED') NOT NULL DEFAULT 'PENDING' COMMENT '删除请求状态',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已认领次数',
  `next_attempt_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可认领时间',
  `last_error` VARCHAR(128) DEFAULT NULL COMMENT '最近一次失败摘要',
  `claimed_at` DATETIME DEFAULT NULL COMMENT '最近认领时间',
  `lease_expires_at` DATETIME DEFAULT NULL COMMENT '当前认领租约到期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_deletion_request_asset` (`asset_id`),
  KEY `idx_asset_deletion_request_due` (`status`, `next_attempt_at`, `id`),
  KEY `idx_asset_deletion_request_lease` (`status`, `lease_expires_at`),
  CONSTRAINT `fk_asset_deletion_request_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产耐久删除请求';
