ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `user_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(255) DEFAULT NULL COMMENT 'Argon2id 密码哈希，OAuth-only 账户为空',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱，verified-email 账户发现的匹配键',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像引用路径',
  `theme_preference` VARCHAR(16) NOT NULL DEFAULT 'system' COMMENT '主题偏好',
  `revision` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观并发版本号',
  `last_operation_id` VARCHAR(64) DEFAULT NULL COMMENT '最近一次成功 mutation 的 operationId',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account_username` (`username`),
  UNIQUE KEY `uk_user_account_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证账户';

CREATE TABLE `oauth_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT 'OAuth Provider 标识',
  `provider_subject` VARCHAR(128) NOT NULL COMMENT 'Provider 内主体标识',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_binding_identity` (`provider`, `provider_subject`),
  UNIQUE KEY `uk_oauth_binding_account_provider` (`account_id`, `provider`),
  CONSTRAINT `fk_oauth_binding_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth 身份绑定';

CREATE TABLE `asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `kind` VARCHAR(32) NOT NULL COMMENT '资产用途类别',
  `object_key` VARCHAR(128) NOT NULL COMMENT '对象存储不可预测唯一键',
  `media_type` VARCHAR(128) NOT NULL COMMENT '媒体类型',
  `byte_size` BIGINT NOT NULL COMMENT '字节大小',
  `width` INT DEFAULT NULL COMMENT '图片宽度，适用时',
  `height` INT DEFAULT NULL COMMENT '图片高度，适用时',
  `status` ENUM('PENDING_UPLOAD','READY') NOT NULL DEFAULT 'PENDING_UPLOAD' COMMENT '资产状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_object_key` (`object_key`),
  KEY `idx_asset_status_created` (`status`, `created_at`),
  KEY `idx_asset_account_created` (`account_id`, `created_at`),
  CONSTRAINT `fk_asset_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储资产引用';

CREATE TABLE `attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `asset_id` BIGINT NOT NULL COMMENT '二进制内容所属资产',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `extracted_text` MEDIUMTEXT DEFAULT NULL COMMENT '可用于检索的文本内容',
  `scope_type` VARCHAR(32) DEFAULT NULL COMMENT '绑定作用域类型',
  `scope_id` BIGINT DEFAULT NULL COMMENT '绑定作用域ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_attachment_account_created` (`account_id`, `created_at`),
  KEY `idx_attachment_scope` (`scope_type`, `scope_id`),
  CONSTRAINT `fk_attachment_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_attachment_asset` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试上下文附件';

CREATE TABLE `resume` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `raw_text` MEDIUMTEXT NOT NULL COMMENT 'PDF 提取的原始文本',
  `parsed_skills` TEXT NOT NULL COMMENT '解析出的技能 JSON 数组',
  `parsed_projects` TEXT NOT NULL COMMENT '解析出的项目 JSON 数组',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_account` (`account_id`),
  CONSTRAINT `fk_resume_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';

CREATE TABLE `position_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT DEFAULT NULL COMMENT '自建岗位所属账户，空表示内置模板',
  `name` VARCHAR(100) NOT NULL COMMENT '岗位名称',
  `system_prompt` TEXT NOT NULL COMMENT '系统提示词',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_template_name` (`name`),
  KEY `idx_position_template_account` (`account_id`),
  CONSTRAINT `fk_position_template_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位模板表';

CREATE TABLE `interview_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `resume_id` BIGINT NOT NULL COMMENT '简历ID',
  `position_id` BIGINT NOT NULL COMMENT '岗位模板ID',
  `target_position` VARCHAR(100) NOT NULL COMMENT '目标岗位',
  `model_execution_snapshot_id` BIGINT NOT NULL COMMENT '开面冻结的模型执行快照',
  `status` ENUM('ongoing','generating','finished') NOT NULL DEFAULT 'ongoing' COMMENT '会话状态',
  `summary` TEXT COMMENT '上下文压缩摘要',
  `summary_report` TEXT COMMENT '评估报告',
  `jd_text` MEDIUMTEXT COMMENT '职位描述文本',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_account` (`account_id`),
  KEY `idx_session_resume_id` (`resume_id`),
  KEY `idx_session_position_id` (`position_id`),
  CONSTRAINT `fk_session_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_session_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`),
  CONSTRAINT `fk_session_position` FOREIGN KEY (`position_id`) REFERENCES `position_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';

CREATE TABLE `interview_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '会话ID',
  `role` ENUM('system','user','assistant') NOT NULL COMMENT '消息角色',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `seq_num` INT NOT NULL COMMENT '会话内消息序号',
  `score` TINYINT DEFAULT NULL COMMENT '答题评分',
  `hint` VARCHAR(255) DEFAULT NULL COMMENT '答题建议',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_message_session_id` (`session_id`),
  KEY `idx_message_session_seq` (`session_id`, `seq_num`),
  CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试消息表';

CREATE TABLE `interview_stage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '会话ID',
  `stage_name` ENUM('warmup','technical','deep_dive','closing') NOT NULL COMMENT '阶段名',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `ended_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_interview_stage_session_id` (`session_id`),
  KEY `idx_interview_stage_session_started_at` (`session_id`, `started_at`),
  CONSTRAINT `fk_interview_stage_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试阶段表';

CREATE TABLE `retrieval_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scope_type` VARCHAR(32) NOT NULL COMMENT '检索作用域类型',
  `scope_id` BIGINT NOT NULL COMMENT '检索作用域ID',
  `ordinal` INT NOT NULL COMMENT '作用域内文本块顺序',
  `content` MEDIUMTEXT NOT NULL COMMENT '可重建文本块',
  `content_hash` CHAR(64) NOT NULL COMMENT '文本块SHA-256',
  `embedding_model` VARCHAR(128) DEFAULT NULL COMMENT '生成向量的模型或算法版本',
  `embedding_dimensions` INT DEFAULT NULL COMMENT '向量维度',
  `embedding_json` LONGTEXT DEFAULT NULL COMMENT '可恢复的向量快照',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_retrieval_chunk_scope_ordinal` (`scope_type`, `scope_id`, `ordinal`),
  KEY `idx_retrieval_chunk_scope` (`scope_type`, `scope_id`),
  KEY `idx_retrieval_chunk_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检索可重建文本块';

CREATE TABLE `score_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `session_id` BIGINT NOT NULL COMMENT '面试会话ID',
  `technical_score` TINYINT DEFAULT NULL COMMENT '技术能力分',
  `expression_score` TINYINT DEFAULT NULL COMMENT '表达清晰度分',
  `logic_score` TINYINT DEFAULT NULL COMMENT '逻辑思维分',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_history_session_id` (`session_id`),
  KEY `idx_score_history_account` (`account_id`),
  CONSTRAINT `fk_score_history_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_score_history_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分历史表';

CREATE TABLE `account_weakness` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `session_id` BIGINT NOT NULL COMMENT '来源会话ID',
  `category` VARCHAR(64) NOT NULL COMMENT '薄弱点分类',
  `description` TEXT NOT NULL COMMENT '薄弱点描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_account_weakness_account` (`account_id`),
  KEY `idx_account_weakness_session` (`session_id`),
  CONSTRAINT `fk_account_weakness_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_account_weakness_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户薄弱点表';

CREATE TABLE `artifact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `kind` VARCHAR(64) NOT NULL COMMENT '成果类别',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact_account_kind` (`account_id`, `kind`),
  CONSTRAINT `fk_artifact_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式成果';

CREATE TABLE `artifact_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `artifact_id` BIGINT NOT NULL COMMENT '所属成果ID',
  `version_number` INT NOT NULL COMMENT '成果版本序号',
  `asset_id` BIGINT DEFAULT NULL COMMENT '版本内容所属资产',
  `provenance_json` TEXT DEFAULT NULL COMMENT '业务来源信息 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact_version_number` (`artifact_id`, `version_number`),
  CONSTRAINT `fk_artifact_version_artifact` FOREIGN KEY (`artifact_id`) REFERENCES `artifact` (`id`),
  CONSTRAINT `fk_artifact_version_asset` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式成果不可变版本';


CREATE TABLE `provider_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT 'Provider 标识',
  `scope_key` VARCHAR(255) NOT NULL COMMENT '凭据作用域：SYSTEM 或账户自定义端点根地址',
  `api_key_encrypted` VARCHAR(512) NOT NULL COMMENT 'AES-GCM 加密后的 API Key',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_provider_credential_scope` (`account_id`, `provider`, `scope_key`),
  CONSTRAINT `fk_provider_credential_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户级 BYOK 模型凭据';

CREATE TABLE `model_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT 'Provider 标识',
  `model` VARCHAR(64) NOT NULL COMMENT '主模型ID',
  `credential_id` BIGINT DEFAULT NULL COMMENT '凭据引用，空表示 SYSTEM 凭据',
  `custom_endpoint_url` VARCHAR(255) DEFAULT NULL COMMENT 'BYOK 协议端点根地址',
  `reasoning_level` VARCHAR(16) DEFAULT 'AUTO' COMMENT '默认推理深度 AUTO/LOW/MEDIUM/HIGH/XHIGH/MAX',
  `effective_parameters_json` TEXT NOT NULL COMMENT '生效参数默认值 JSON',
  `model_capability_json` TEXT DEFAULT NULL COMMENT '自定义协议当前模型最近一次确认的能力投影 JSON',
  `fallback_capabilities_json` TEXT NOT NULL COMMENT '有序回退模型能力投影 JSON 数组（同 Provider 同凭据边界）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_profile_account` (`account_id`),
  CONSTRAINT `fk_model_profile_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_model_profile_credential` FOREIGN KEY (`credential_id`) REFERENCES `provider_credential` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户级模型执行配置';

CREATE TABLE `model_execution_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `profile_id` BIGINT NOT NULL COMMENT '冻结时引用的 ModelProfile',
  `provider` VARCHAR(32) NOT NULL COMMENT 'Provider 标识',
  `model` VARCHAR(64) NOT NULL COMMENT '模型ID',
  `reasoning_level` VARCHAR(16) NOT NULL COMMENT 'AUTO/LOW/MEDIUM/HIGH/XHIGH/MAX',
  `effective_parameters_json` TEXT NOT NULL COMMENT '冻结的生效参数 JSON',
  `capability_version` VARCHAR(32) NOT NULL COMMENT '冻结时的能力目录版本',
  `model_capability_json` TEXT NOT NULL COMMENT '冻结的当前模型能力投影 JSON',
  `fallback_capabilities_json` TEXT NOT NULL COMMENT '冻结的有序回退模型能力投影 JSON 数组',
  `credential_id` BIGINT DEFAULT NULL COMMENT '冻结的凭据引用，空表示 SYSTEM 凭据',
  `custom_endpoint_url` VARCHAR(255) DEFAULT NULL COMMENT '冻结的自定义端点根地址',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_snapshot_account_created` (`account_id`, `created_at`),
  CONSTRAINT `fk_snapshot_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `fk_snapshot_profile` FOREIGN KEY (`profile_id`) REFERENCES `model_profile` (`id`),
  CONSTRAINT `fk_snapshot_credential` FOREIGN KEY (`credential_id`) REFERENCES `provider_credential` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变模型执行快照';

CREATE TABLE `background_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` CHAR(36) NOT NULL COMMENT '对外任务ID',
  `type` VARCHAR(64) NOT NULL COMMENT '任务类型',
  `account_id` BIGINT NOT NULL COMMENT '任务所属账户',
  `subject_id` BIGINT NOT NULL COMMENT '业务对象ID',
  `operation_key` VARCHAR(160) NOT NULL COMMENT '幂等操作键',
  `payload_json` TEXT NOT NULL COMMENT '任务参数快照',
  `status` ENUM('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已执行次数',
  `max_attempts` INT NOT NULL DEFAULT 3 COMMENT '最大尝试次数',
  `last_error` TEXT DEFAULT NULL COMMENT '最近一次脱敏错误摘要',
  `claimed_at` DATETIME DEFAULT NULL COMMENT '最近一次认领时间',
  `lease_expires_at` DATETIME DEFAULT NULL COMMENT '当前 attempt 的租约到期时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '终态时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_background_job_job_id` (`job_id`),
  UNIQUE KEY `uk_background_job_operation_key` (`operation_key`),
  KEY `idx_background_job_account_created` (`account_id`, `created_at`),
  KEY `idx_background_job_status_lease` (`status`, `lease_expires_at`),
  CONSTRAINT `fk_background_job_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持久化后台任务';

CREATE TABLE `job_attempt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` CHAR(36) NOT NULL COMMENT '所属任务对外ID',
  `attempt_number` INT NOT NULL COMMENT '尝试序号',
  `status` ENUM('RUNNING','SUCCEEDED','FAILED','INTERRUPTED') NOT NULL COMMENT '尝试状态',
  `started_at` DATETIME NOT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `failure_summary` TEXT DEFAULT NULL COMMENT '脱敏失败摘要',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_attempt_number` (`job_id`, `attempt_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务尝试记录';

ALTER TABLE `interview_session`
  ADD CONSTRAINT `fk_session_model_execution_snapshot`
  FOREIGN KEY (`model_execution_snapshot_id`) REFERENCES `model_execution_snapshot` (`id`);

ALTER TABLE `job_attempt`
  ADD CONSTRAINT `fk_job_attempt_job`
  FOREIGN KEY (`job_id`) REFERENCES `background_job` (`job_id`);

-- Spring Modulith 2.1.1 event publication schema (official v2 schema-mysql.sql).
-- Auto schema initialization is disabled; Flyway is the only DDL owner.
CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION
(
  ID                     VARCHAR(36) NOT NULL,
  LISTENER_ID            VARCHAR(512) NOT NULL,
  EVENT_TYPE             VARCHAR(512) NOT NULL,
  SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
  PUBLICATION_DATE       TIMESTAMP(6) NOT NULL,
  COMPLETION_DATE        TIMESTAMP(6) DEFAULT NULL NULL,
  STATUS                 VARCHAR(20),
  COMPLETION_ATTEMPTS    INT,
  LAST_RESUBMISSION_DATE TIMESTAMP(6) DEFAULT NULL NULL,
  PRIMARY KEY (ID),
  INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
);
