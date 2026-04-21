CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `llm_provider` VARCHAR(32) NOT NULL DEFAULT 'deepseek' COMMENT 'LLM Provider',
  `llm_model` VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat' COMMENT 'LLM 模型',
  `llm_api_key_encrypted` VARCHAR(512) DEFAULT NULL COMMENT '加密后的用户 API Key',
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
    'ALTER TABLE `user` ADD COLUMN `llm_api_key_encrypted` VARCHAR(512) DEFAULT NULL COMMENT ''加密后的用户 API Key''',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'llm_api_key_encrypted'
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
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_user_id` (`user_id`),
  CONSTRAINT `fk_resume_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';

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
  `status` ENUM('ongoing','finished') NOT NULL DEFAULT 'ongoing' COMMENT '会话状态',
  `summary_report` TEXT COMMENT '评估报告',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_user_id` (`user_id`),
  KEY `idx_session_resume_id` (`resume_id`),
  KEY `idx_session_position_id` (`position_id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_session_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`),
  CONSTRAINT `fk_session_position` FOREIGN KEY (`position_id`) REFERENCES `position_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';

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

CREATE TABLE IF NOT EXISTS `interview_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '会话ID',
  `role` ENUM('system','user','assistant') NOT NULL COMMENT '消息角色',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `seq_num` INT NOT NULL COMMENT '会话内消息序号',
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
