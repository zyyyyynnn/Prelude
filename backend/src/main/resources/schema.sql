CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
  `theme_preference` VARCHAR(16) NOT NULL DEFAULT 'system' COMMENT '主题偏好',
  `llm_provider` VARCHAR(32) NOT NULL DEFAULT 'deepseek' COMMENT 'LLM Provider',
  `llm_model` VARCHAR(64) NOT NULL DEFAULT 'deepseek-v4-pro' COMMENT 'LLM 模型',
  `llm_base_url` VARCHAR(255) DEFAULT NULL COMMENT '用户自定义 OpenAI-compatible API 根地址',
  `llm_api_key_encrypted` VARCHAR(512) DEFAULT NULL COMMENT '加密后的用户 API Key',
  `llm_max_tokens` INT DEFAULT NULL COMMENT 'LLM 最大输出 Token',
  `llm_thinking_depth` VARCHAR(20) DEFAULT NULL COMMENT 'LLM 思考深度',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `llm_provider` VARCHAR(32) NOT NULL DEFAULT ''deepseek'' COMMENT ''LLM Provider''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_provider'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `llm_model` VARCHAR(64) NOT NULL DEFAULT ''deepseek-chat'' COMMENT ''LLM 模型''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_model'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `llm_base_url` VARCHAR(255) DEFAULT NULL COMMENT ''用户自定义 OpenAI-compatible API 根地址''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_base_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT ''头像 URL''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'avatar_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `theme_preference` VARCHAR(16) NOT NULL DEFAULT ''system'' COMMENT ''主题偏好''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'theme_preference'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `llm_api_key_encrypted` VARCHAR(512) DEFAULT NULL COMMENT ''加密后的用户 API Key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_api_key_encrypted'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `llm_max_tokens` INT DEFAULT NULL COMMENT ''LLM 最大输出 Token''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_max_tokens'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `llm_thinking_depth` VARCHAR(20) DEFAULT NULL COMMENT ''LLM 思考深度''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_thinking_depth'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `resume` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `parsed_skills` TEXT COMMENT '解析出的技能',
  `parsed_projects` TEXT COMMENT '解析出的项目',
  `raw_text` MEDIUMTEXT COMMENT 'PDF原始文本',
  `document_json` LONGTEXT COMMENT 'ResumeDocument 结构化真源',
  `document_version` INT COMMENT '结构化文档版本',
  `source_type` VARCHAR(32) COMMENT 'pdf_import/editor/fixture',
  `plain_text_projection` MEDIUMTEXT COMMENT '确定性文本投影缓存',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_user_id` (`user_id`),
  CONSTRAINT `fk_resume_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `document_json` LONGTEXT NULL COMMENT ''ResumeDocument 结构化真源''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'document_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `document_version` INT NULL COMMENT ''结构化文档版本''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'document_version'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `source_type` VARCHAR(32) NULL COMMENT ''pdf_import/editor/fixture''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'source_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `plain_text_projection` MEDIUMTEXT NULL COMMENT ''确定性文本投影缓存''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'plain_text_projection'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `position_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '岗位名称',
  `system_prompt` TEXT NOT NULL COMMENT '系统提示词',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位模板表';

CREATE TABLE IF NOT EXISTS `interview_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `resume_id` BIGINT NOT NULL COMMENT '简历ID',
  `position_id` BIGINT NOT NULL COMMENT '岗位模板ID',
  `target_position` VARCHAR(100) NOT NULL COMMENT '目标岗位',
  `llm_provider` VARCHAR(32) NOT NULL DEFAULT 'deepseek' COMMENT '会话使用的 Provider 快照',
  `llm_model` VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat' COMMENT '会话使用的模型快照',
  `prompt_versions_json` VARCHAR(512) DEFAULT NULL COMMENT '会话使用的 Prompt 版本快照',
  `status` ENUM('ongoing','generating','finished') NOT NULL DEFAULT 'ongoing' COMMENT '会话状态',
  `summary` TEXT COMMENT '上下文压缩摘要',
  `summary_report` TEXT COMMENT '评估报告',
  `jd_text` MEDIUMTEXT COMMENT '职位描述文本',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_user_id` (`user_id`),
  KEY `idx_session_resume_id` (`resume_id`),
  KEY `idx_session_position_id` (`position_id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_session_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`),
  CONSTRAINT `fk_session_position` FOREIGN KEY (`position_id`) REFERENCES `position_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';

-- 幂等扩展 status 枚举：补上 `generating`（RabbitMQ 异步报告任务已发布但尚未完成消费的中间态）
-- 仅在旧枚举状态下执行 MODIFY COLUMN；已是目标枚举则跳过，保证重复执行 schema.sql 不破坏数据。
SET @sql = (
  SELECT IF(
    COUNT(*) = 1
    AND MAX(COLUMN_TYPE) <> 'enum(''ongoing'',''generating'',''finished'')',
    'ALTER TABLE `interview_session` MODIFY COLUMN `status` ENUM(''ongoing'',''generating'',''finished'') NOT NULL DEFAULT ''ongoing'' COMMENT ''会话状态''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'interview_session'
    AND COLUMN_NAME = 'status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_session` ADD COLUMN `llm_provider` VARCHAR(32) NOT NULL DEFAULT ''deepseek'' COMMENT ''会话使用的 Provider 快照''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'llm_provider'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_session` ADD COLUMN `llm_model` VARCHAR(64) NOT NULL DEFAULT ''deepseek-chat'' COMMENT ''会话使用的模型快照''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'llm_model'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_session` ADD COLUMN `prompt_versions_json` VARCHAR(512) DEFAULT NULL COMMENT ''会话使用的 Prompt 版本快照''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'prompt_versions_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_session` ADD COLUMN `summary` TEXT COMMENT ''上下文压缩摘要''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'summary'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `retrieval_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scope_type` VARCHAR(32) NOT NULL COMMENT '检索作用域类型',
  `scope_id` BIGINT NOT NULL COMMENT '检索作用域ID',
  `ordinal` INT NOT NULL COMMENT '作用域内文本块顺序',
  `content` MEDIUMTEXT NOT NULL COMMENT '可重建文本块',
  `content_hash` CHAR(64) NOT NULL COMMENT '文本块SHA-256',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_retrieval_chunk_scope_ordinal` (`scope_type`, `scope_id`, `ordinal`),
  KEY `idx_retrieval_chunk_scope` (`scope_type`, `scope_id`),
  KEY `idx_retrieval_chunk_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检索可重建文本块';

CREATE TABLE IF NOT EXISTS `async_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` CHAR(36) NOT NULL COMMENT '对外任务ID',
  `type` VARCHAR(64) NOT NULL COMMENT '任务类型',
  `user_id` BIGINT NOT NULL COMMENT '任务所有者',
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
  KEY `idx_async_job_user_created` (`user_id`, `created_at`),
  KEY `idx_async_job_status_updated` (`status`, `updated_at`),
  CONSTRAINT `fk_async_job_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务状态与幂等记录';

CREATE TABLE IF NOT EXISTS `interview_message` (
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

CREATE TABLE IF NOT EXISTS `interview_stage` (
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

CREATE TABLE IF NOT EXISTS `score_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `session_id` BIGINT NOT NULL COMMENT '面试会话ID',
  `technical_score` TINYINT DEFAULT NULL COMMENT '技术能力分',
  `expression_score` TINYINT DEFAULT NULL COMMENT '表达清晰度分',
  `logic_score` TINYINT DEFAULT NULL COMMENT '逻辑思维分',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_history_session_id` (`session_id`),
  KEY `idx_score_history_user_id` (`user_id`),
  CONSTRAINT `fk_score_history_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_score_history_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分历史表';

CREATE TABLE IF NOT EXISTS `user_weakness` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `session_id` BIGINT NOT NULL COMMENT '来源会话ID',
  `category` VARCHAR(64) NOT NULL COMMENT '薄弱点分类',
  `description` TEXT NOT NULL COMMENT '薄弱点描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_weakness_user_id` (`user_id`),
  KEY `idx_user_weakness_session_id` (`session_id`),
  CONSTRAINT `fk_user_weakness_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_user_weakness_session` FOREIGN KEY (`session_id`) REFERENCES `interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户薄弱点表';

CREATE TABLE IF NOT EXISTS `llm_provider_config` (
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

-- 高频 WHERE user_id + ORDER BY created_at 查询优化（information_schema 兼容写法）
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_session_user_created ON interview_session (user_id, created_at)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND INDEX_NAME = 'idx_session_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_score_user_created ON score_history (user_id, created_at)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'score_history' AND INDEX_NAME = 'idx_score_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_weakness_user_created ON user_weakness (user_id, created_at)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_weakness' AND INDEX_NAME = 'idx_weakness_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_resume_user_created ON resume (user_id, created_at)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND INDEX_NAME = 'idx_resume_user_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_message` ADD COLUMN `score` TINYINT DEFAULT NULL COMMENT ''答题评分''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_message' AND COLUMN_NAME = 'score'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_message` ADD COLUMN `hint` VARCHAR(255) DEFAULT NULL COMMENT ''答题建议''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_message' AND COLUMN_NAME = 'hint'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `interview_session` ADD COLUMN `jd_text` MEDIUMTEXT COMMENT ''职位描述文本''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'jd_text'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
