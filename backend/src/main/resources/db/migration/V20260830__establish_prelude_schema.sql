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
  `llm_provider` VARCHAR(32) NOT NULL DEFAULT 'deepseek' COMMENT 'LLM Provider',
  `llm_model` VARCHAR(64) NOT NULL DEFAULT 'deepseek-v4-pro' COMMENT 'LLM 模型',
  `llm_base_url` VARCHAR(255) DEFAULT NULL COMMENT '账户自定义模型接口根地址',
  `llm_api_key_encrypted` VARCHAR(512) DEFAULT NULL COMMENT '加密后的账户 API Key',
  `llm_max_tokens` INT DEFAULT NULL COMMENT 'LLM 最大输出 Token',
  `llm_thinking_depth` VARCHAR(20) DEFAULT NULL COMMENT 'LLM 思考深度',
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
  CONSTRAINT `fk_attachment_asset` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`)
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
  `llm_provider` VARCHAR(32) NOT NULL DEFAULT 'deepseek' COMMENT '会话使用的 Provider 快照',
  `llm_model` VARCHAR(64) NOT NULL DEFAULT 'deepseek-v4-pro' COMMENT '会话使用的模型快照',
  `llm_thinking_depth` VARCHAR(20) DEFAULT NULL COMMENT '会话使用的思考深度快照',
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

CREATE TABLE `async_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` CHAR(36) NOT NULL COMMENT '对外任务ID',
  `type` VARCHAR(64) NOT NULL COMMENT '任务类型',
  `account_id` BIGINT NOT NULL COMMENT '任务所属账户',
  `subject_id` BIGINT NOT NULL COMMENT '业务对象ID',
  `idempotency_key` VARCHAR(160) NOT NULL COMMENT '幂等键',
  `status` ENUM('pending','running','succeeded','failed') NOT NULL DEFAULT 'pending' COMMENT '任务状态',
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '已执行次数',
  `payload_json` TEXT NOT NULL COMMENT '任务参数快照',
  `last_error` TEXT DEFAULT NULL COMMENT '最近一次错误',
  `dispatched_at` DATETIME DEFAULT NULL COMMENT '最近一次投递时间',
  `started_at` DATETIME DEFAULT NULL COMMENT '最近一次开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '终态时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_async_job_job_id` (`job_id`),
  UNIQUE KEY `uk_async_job_idempotency_key` (`idempotency_key`),
  KEY `idx_async_job_account_created` (`account_id`, `created_at`),
  KEY `idx_async_job_status_updated` (`status`, `updated_at`),
  CONSTRAINT `fk_async_job_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务状态与幂等记录';

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

CREATE TABLE `llm_provider_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_key` VARCHAR(32) NOT NULL COMMENT 'Provider 标识',
  `display_name` VARCHAR(64) NOT NULL COMMENT '展示名称',
  `base_url` VARCHAR(255) NOT NULL COMMENT 'API 端点',
  `available_models` TEXT NOT NULL COMMENT '可选模型 JSON 数组',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_llm_provider_config_provider_key` (`provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM Provider 配置表';

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
