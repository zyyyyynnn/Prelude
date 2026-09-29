-- Resume patch review: the document gets a revision chain, the assistant run gets a
-- step-by-step record the product can replay, and a model proposal becomes a reviewable
-- object that only a user decision can turn into a new revision.
--
-- The tool-call tables created one migration earlier are folded into resume_agent_step:
-- a run's steps are the trace, so keeping both would be two models of one fact.

ALTER TABLE `resume_conversation`
  ADD KEY `idx_resume_conversation_resume` (`resume_id`),
  ADD CONSTRAINT `fk_resume_conversation_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`);

-- One assistant run: the frozen inputs it was allowed to see, and where it ended.
CREATE TABLE `resume_agent_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `turn_id` BIGINT NOT NULL COMMENT '触发本次运行的指令轮',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `resume_id` BIGINT NOT NULL COMMENT '运行所依据的简历',
  `base_revision` INT NOT NULL COMMENT '冻结时的简历版本序号',
  `model_execution_snapshot_id` BIGINT NOT NULL COMMENT '冻结的模型执行快照',
  `prompt_id` VARCHAR(80) NOT NULL COMMENT '提示词标识',
  `status` VARCHAR(20) NOT NULL COMMENT 'running|proposal_ready|invalid|failed|cancelled',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败原因，仅 failed',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_agent_run_turn` (`turn_id`, `id`),
  CONSTRAINT `fk_resume_agent_run_turn` FOREIGN KEY (`turn_id`) REFERENCES `resume_turn` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_resume_agent_run_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `resume_conversation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_resume_agent_run_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`),
  CONSTRAINT `fk_resume_agent_run_snapshot` FOREIGN KEY (`model_execution_snapshot_id`) REFERENCES `model_execution_snapshot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手运行';

-- The trace the product replays: one row per action the model actually took.
CREATE TABLE `resume_agent_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` BIGINT NOT NULL COMMENT '所属运行',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '运行内顺序',
  `kind` VARCHAR(20) NOT NULL COMMENT 'think|read|search|write|run|policy|proposal',
  `label` VARCHAR(200) NOT NULL COMMENT '步骤标题',
  `tool_name` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '模型发起的工具名，非工具步骤为 ''',
  `arguments_json` TEXT DEFAULT NULL COMMENT '工具入参 JSON',
  `result_excerpt` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '结果摘要',
  `files_json` TEXT DEFAULT NULL COMMENT '本步写过的文件与行差 [{name,add,del}]',
  `chips_json` TEXT DEFAULT NULL COMMENT '行内芯片 [string]',
  `detail_json` TEXT DEFAULT NULL COMMENT '展开明细 [string]',
  `badge` VARCHAR(80) DEFAULT NULL COMMENT '行尾徽标文案',
  `badge_tone` VARCHAR(20) DEFAULT NULL COMMENT 'default|add|error',
  `state` VARCHAR(20) NOT NULL COMMENT 'running|done|error',
  `error` VARCHAR(500) DEFAULT NULL COMMENT '本步失败原因，仅 error',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_agent_step_order` (`run_id`, `sort_order`),
  CONSTRAINT `fk_resume_agent_step_run` FOREIGN KEY (`run_id`) REFERENCES `resume_agent_run` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历助手执行步骤';

-- What the model wants to change. Nothing reaches the document without a decision here.
CREATE TABLE `resume_patch_proposal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` BIGINT NOT NULL COMMENT '产生本提案的运行',
  `resume_id` BIGINT NOT NULL COMMENT '目标简历',
  `conversation_id` BIGINT NOT NULL COMMENT '所属会话',
  `base_revision` INT NOT NULL COMMENT '提案所依据的版本序号',
  `affected_block_ids` TEXT NOT NULL COMMENT '被改动的块 id JSON 数组',
  `reason` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '提案理由，用户面唯一可见的模型陈述',
  `operations_json` MEDIUMTEXT NOT NULL COMMENT '块级操作 JSON 数组',
  `fact_risk_json` TEXT DEFAULT NULL COMMENT '未证实事实风险 [{blockId,fact}]',
  `status` VARCHAR(20) NOT NULL COMMENT 'pending|accepted|rejected|stale|invalid',
  `decision_note` VARCHAR(500) DEFAULT NULL COMMENT '拒绝或失效说明',
  `decided_at` DATETIME DEFAULT NULL COMMENT '决策时间',
  `decided_by_account_id` BIGINT DEFAULT NULL COMMENT '决策账户',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_resume_proposal_conversation` (`conversation_id`, `id`),
  KEY `idx_resume_proposal_pending` (`resume_id`, `status`),
  CONSTRAINT `fk_resume_proposal_run` FOREIGN KEY (`run_id`) REFERENCES `resume_agent_run` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_resume_proposal_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`),
  CONSTRAINT `fk_resume_proposal_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `resume_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历补丁提案';

-- The resume document as a block tree with stable ids. Revision 1 is seeded from the
-- imported PDF parse; every later revision is written by a user edit or an accepted
-- proposal, never by a model directly.
CREATE TABLE `resume_revision` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `resume_id` BIGINT NOT NULL COMMENT '所属简历ID',
  `revision_number` INT NOT NULL COMMENT '简历内版本序号',
  `document_json` MEDIUMTEXT NOT NULL COMMENT '块树 AST，块 id 跨版本稳定',
  `summary` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '本版本相对上一版的说明',
  `origin` VARCHAR(20) NOT NULL COMMENT 'imported|user_edit|agent_patch',
  `proposal_id` BIGINT DEFAULT NULL COMMENT '产生本版本的补丁提案，仅 agent_patch 使用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_revision_number` (`resume_id`, `revision_number`),
  KEY `idx_resume_revision_resume` (`resume_id`, `id`),
  CONSTRAINT `fk_resume_revision_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`),
  CONSTRAINT `fk_resume_revision_proposal` FOREIGN KEY (`proposal_id`) REFERENCES `resume_patch_proposal` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历不可变版本';

-- A turn now carries the blocks it was about, the attachments it was given, and a reason
-- when it fails; the queue needs a per-conversation order the database actually enforces.
ALTER TABLE `resume_turn`
  ADD COLUMN `block_ids` TEXT DEFAULT NULL COMMENT '指令指向的块 id JSON 数组，NULL 表示整份简历' AFTER `instruction`,
  ADD COLUMN `attachment_ids` TEXT DEFAULT NULL COMMENT '随指令送出的资产 id JSON 数组' AFTER `block_ids`,
  ADD COLUMN `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败原因，仅 failed' AFTER `completed_at`,
  MODIFY COLUMN `status` VARCHAR(20) NOT NULL COMMENT 'queued|running|done|failed|cancelled',
  ADD UNIQUE KEY `uk_resume_turn_queue_position` (`conversation_id`, `queue_position`);

DROP TABLE `resume_tool_diff`;
DROP TABLE `resume_tool_call`;
DROP TABLE `resume_tool_group`;
