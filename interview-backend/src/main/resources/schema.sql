CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

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
