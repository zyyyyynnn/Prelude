-- Resume workspace conversations: one assistant thread per work session.
-- A turn is one user instruction; each assistant message may own one tool-call group.

CREATE TABLE `resume_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `resume_id` BIGINT DEFAULT NULL COMMENT '关联简历，空表示尚未挂接',
  `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '会话标题',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_conversation_account` (`account_id`, `updated_at`),
  CONSTRAINT `fk_resume_conversation_account` FOREIGN KEY (`account_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历工作会话';

CREATE TABLE `resume_turn` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `account_id` BIGINT NOT NULL COMMENT '所属账户ID',
  `instruction` MEDIUMTEXT NOT NULL COMMENT '用户完整指令',
  `status` VARCHAR(20) NOT NULL COMMENT 'queued|running|done',
  `queue_position` INT NOT NULL DEFAULT 0 COMMENT '同会话排队序',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_turn_conversation` (`conversation_id`, `id`),
  CONSTRAINT `fk_resume_turn_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `resume_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手指令轮';

CREATE TABLE `resume_assistant_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `turn_id` BIGINT NOT NULL COMMENT '指令轮ID',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `seq_num` INT NOT NULL COMMENT '轮内消息序',
  `content` MEDIUMTEXT NOT NULL COMMENT '助手正文',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_message_turn_seq` (`turn_id`, `seq_num`),
  KEY `idx_resume_message_conversation` (`conversation_id`, `id`),
  CONSTRAINT `fk_resume_message_turn` FOREIGN KEY (`turn_id`) REFERENCES `resume_turn` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手消息';

CREATE TABLE `resume_tool_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` BIGINT NOT NULL COMMENT '归属助手消息',
  `label` VARCHAR(120) NOT NULL COMMENT '组头文案',
  `status` VARCHAR(20) NOT NULL COMMENT 'running|done',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_tool_group_message` (`message_id`),
  CONSTRAINT `fk_resume_tool_group_message` FOREIGN KEY (`message_id`) REFERENCES `resume_assistant_message` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手 Toolcall 组';

CREATE TABLE `resume_tool_call` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` BIGINT NOT NULL COMMENT '所属 toolcall 组',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '组内顺序',
  `kind` VARCHAR(20) NOT NULL COMMENT 'think|write|run|read',
  `label` VARCHAR(200) NOT NULL COMMENT '工具行标题',
  `chip` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '行内芯片',
  `detail` TEXT NOT NULL COMMENT '展开明细 JSON',
  `state` VARCHAR(20) NOT NULL COMMENT 'pending|running|done|error',
  `error` VARCHAR(500) DEFAULT NULL COMMENT '单条失败原因',
  PRIMARY KEY (`id`),
  KEY `idx_resume_tool_call_group` (`group_id`, `sort_order`),
  CONSTRAINT `fk_resume_tool_call_group` FOREIGN KEY (`group_id`) REFERENCES `resume_tool_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手工具调用';

CREATE TABLE `resume_tool_diff` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` BIGINT NOT NULL COMMENT '所属 toolcall 组',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `add_count` INT NOT NULL DEFAULT 0 COMMENT '新增行',
  `del_count` INT NOT NULL DEFAULT 0 COMMENT '删除行',
  PRIMARY KEY (`id`),
  KEY `idx_resume_tool_diff_group` (`group_id`, `sort_order`),
  CONSTRAINT `fk_resume_tool_diff_group` FOREIGN KEY (`group_id`) REFERENCES `resume_tool_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手文件差异芯片';
