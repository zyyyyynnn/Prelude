-- 1. 默认岗位数据与乱码修复/迁移：仅处理已知历史默认岗位乱码值，不依赖自增 id。
UPDATE `position_template`
SET `name` = 'Java 后端工程师',
    `system_prompt` = '你是一名严谨的 Java 后端面试官，请重点考察候选人在 Spring Boot、JVM、MySQL 与分布式系统方面的基础与实践能力。提问要循序渐进，注重项目经历追问。'
WHERE `name` IN (
    CONVERT(0x4A61766120C3A5C290C28EC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0x4A61766120E98D9AE5BAA3EFBFBDEFBFBDE5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0x4A61766120C3A5EFBFBDC5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4),
    CONVERT(0x4A61766120C3A5C290C5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
)
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT 1 FROM `position_template` WHERE `name` = 'Java 后端工程师') AS existing_default_position
  )
ORDER BY `id`
LIMIT 1;

UPDATE `position_template`
SET `name` = '前端工程师',
    `system_prompt` = '你是一名前端工程师岗位的面试官，请重点考察候选人在 Vue、React、浏览器原理、工程化和交互实现方面的理解。提问风格客观直接，注重场景化追问。'
WHERE `name` IN (
    CONVERT(0xC3A5C289C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0xE98D93E5B687EFBFBDEFBFBDE5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0xC3A5E280B0EFBFBDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4),
    CONVERT(0xC3A5E280B0C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
)
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT 1 FROM `position_template` WHERE `name` = '前端工程师') AS existing_default_position
  )
ORDER BY `id`
LIMIT 1;

UPDATE `position_template`
SET `name` = '算法工程师',
    `system_prompt` = '你是一名算法工程师岗位的面试官，请重点考察候选人在数据结构、LeetCode 解题思路以及机器学习基础方面的理解与表达。请根据候选人回答逐步加深难度。'
WHERE `name` IN (
    CONVERT(0xC3A7C2AEC297C3A6C2B3C295C3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0xE7BBA0E6A581E7A1B6E5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0xC3A7C2AEE28094C3A6C2B3E280A2C3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
)
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT 1 FROM `position_template` WHERE `name` = '算法工程师') AS existing_default_position
  )
ORDER BY `id`
LIMIT 1;

INSERT INTO `position_template` (`name`, `system_prompt`)
SELECT 'Java 后端工程师',
       '你是一名严谨的 Java 后端面试官，请重点考察候选人在 Spring Boot、JVM、MySQL 与分布式系统方面的基础与实践能力。提问要循序渐进，注重项目经历追问。'
WHERE NOT EXISTS (
    SELECT 1 FROM `position_template` WHERE `name` = 'Java 后端工程师'
);

UPDATE `interview_session` s
JOIN `position_template` bad_position ON s.`position_id` = bad_position.`id`
JOIN `position_template` default_position ON default_position.`name` = 'Java 后端工程师'
SET s.`position_id` = default_position.`id`,
    s.`target_position` = default_position.`name`
WHERE bad_position.`name` IN (
    CONVERT(0x4A61766120C3A5C290C28EC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0x4A61766120E98D9AE5BAA3EFBFBDEFBFBDE5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0x4A61766120C3A5EFBFBDC5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4),
    CONVERT(0x4A61766120C3A5C290C5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
);

DELETE bad_position
FROM `position_template` bad_position
JOIN `position_template` default_position ON default_position.`name` = 'Java 后端工程师'
WHERE bad_position.`name` IN (
    CONVERT(0x4A61766120C3A5C290C28EC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0x4A61766120E98D9AE5BAA3EFBFBDEFBFBDE5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0x4A61766120C3A5EFBFBDC5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4),
    CONVERT(0x4A61766120C3A5C290C5BDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
);

INSERT INTO `position_template` (`name`, `system_prompt`)
SELECT '前端工程师',
       '你是一名前端工程师岗位的面试官，请重点考察候选人在 Vue、React、浏览器原理、工程化和交互实现方面的理解。提问风格客观直接，注重场景化追问。'
WHERE NOT EXISTS (
    SELECT 1 FROM `position_template` WHERE `name` = '前端工程师'
);

UPDATE `interview_session` s
JOIN `position_template` bad_position ON s.`position_id` = bad_position.`id`
JOIN `position_template` default_position ON default_position.`name` = '前端工程师'
SET s.`position_id` = default_position.`id`,
    s.`target_position` = default_position.`name`
WHERE bad_position.`name` IN (
    CONVERT(0xC3A5C289C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0xE98D93E5B687EFBFBDEFBFBDE5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0xC3A5E280B0EFBFBDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4),
    CONVERT(0xC3A5E280B0C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
);

DELETE bad_position
FROM `position_template` bad_position
JOIN `position_template` default_position ON default_position.`name` = '前端工程师'
WHERE bad_position.`name` IN (
    CONVERT(0xC3A5C289C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0xE98D93E5B687EFBFBDEFBFBDE5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0xC3A5E280B0EFBFBDC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4),
    CONVERT(0xC3A5E280B0C28DC3A7C2ABC2AFC3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
);

INSERT INTO `position_template` (`name`, `system_prompt`)
SELECT '算法工程师',
       '你是一名算法工程师岗位的面试官，请重点考察候选人在数据结构、LeetCode 解题思路以及机器学习基础方面的理解与表达。请根据候选人回答逐步加深难度。'
WHERE NOT EXISTS (
    SELECT 1 FROM `position_template` WHERE `name` = '算法工程师'
);

UPDATE `interview_session` s
JOIN `position_template` bad_position ON s.`position_id` = bad_position.`id`
JOIN `position_template` default_position ON default_position.`name` = '算法工程师'
SET s.`position_id` = default_position.`id`,
    s.`target_position` = default_position.`name`
WHERE bad_position.`name` IN (
    CONVERT(0xC3A7C2AEC297C3A6C2B3C295C3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0xE7BBA0E6A581E7A1B6E5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0xC3A7C2AEE28094C3A6C2B3E280A2C3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
);

DELETE bad_position
FROM `position_template` bad_position
JOIN `position_template` default_position ON default_position.`name` = '算法工程师'
WHERE bad_position.`name` IN (
    CONVERT(0xC3A7C2AEC297C3A6C2B3C295C3A5C2B7C2A5C3A7C2A8C28BC3A5C2B8C288 USING utf8mb4),
    CONVERT(0xE7BBA0E6A581E7A1B6E5AEB8E383A7E296BCE794AFEFBFBD USING utf8mb4),
    CONVERT(0xC3A7C2AEE28094C3A6C2B3E280A2C3A5C2B7C2A5C3A7C2A8E280B9C3A5C2B8CB86 USING utf8mb4)
);

-- 2. LLM provider 默认配置。
INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'deepseek',
       'DeepSeek',
       'https://api.deepseek.com/chat/completions',
        '["deepseek-v4-pro","deepseek-v4-flash"]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'deepseek'
);

UPDATE `llm_provider_config`
SET `available_models` = '["deepseek-v4-pro","deepseek-v4-flash"]'
WHERE `provider_key` = 'deepseek'
  AND `available_models` != '["deepseek-v4-pro","deepseek-v4-flash"]';

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'openai',
       'OpenAI',
       'https://api.openai.com/v1/chat/completions',
       '["gpt-5.5","gpt-5.4"]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'openai'
);

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'anthropic',
       'Anthropic',
       'https://api.anthropic.com/v1/messages',
       '["claude-4.7-opus","claude-4.6-opus"]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'anthropic'
);

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'openai-compatible',
       'OpenAI 兼容协议',
       '',
       '[]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'openai-compatible'
);

UPDATE `llm_provider_config`
SET `display_name` = 'OpenAI 兼容协议'
WHERE `provider_key` = 'openai-compatible'
  AND `display_name` <> 'OpenAI 兼容协议';

-- 3.0 清理或迁移旧的 4 月 demo session 到 6 月

-- a) 如果目标 6 月 session 已存在，则清理掉多余的旧 4 月 session 的子表与本体
DELETE uw
FROM `user_weakness` uw
JOIN `interview_session` old_s ON uw.`session_id` = old_s.`id`
JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `interview_session` new_s ON new_s.`user_id` = u.`id` AND new_s.`target_position` = old_s.`target_position`
WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND new_s.`created_at` IN ('2026-06-16 14:00:00', '2026-06-15 10:00:00', '2026-06-14 16:10:00', '2026-06-12 15:30:00');

DELETE sh
FROM `score_history` sh
JOIN `interview_session` old_s ON sh.`session_id` = old_s.`id`
JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `interview_session` new_s ON new_s.`user_id` = u.`id` AND new_s.`target_position` = old_s.`target_position`
WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND new_s.`created_at` IN ('2026-06-16 14:00:00', '2026-06-15 10:00:00', '2026-06-14 16:10:00', '2026-06-12 15:30:00');

DELETE st
FROM `interview_stage` st
JOIN `interview_session` old_s ON st.`session_id` = old_s.`id`
JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `interview_session` new_s ON new_s.`user_id` = u.`id` AND new_s.`target_position` = old_s.`target_position`
WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND new_s.`created_at` IN ('2026-06-16 14:00:00', '2026-06-15 10:00:00', '2026-06-14 16:10:00', '2026-06-12 15:30:00');

DELETE msg
FROM `interview_message` msg
JOIN `interview_session` old_s ON msg.`session_id` = old_s.`id`
JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `interview_session` new_s ON new_s.`user_id` = u.`id` AND new_s.`target_position` = old_s.`target_position`
WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND new_s.`created_at` IN ('2026-06-16 14:00:00', '2026-06-15 10:00:00', '2026-06-14 16:10:00', '2026-06-12 15:30:00');

DELETE old_s
FROM `interview_session` old_s
JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `interview_session` new_s ON new_s.`user_id` = u.`id` AND new_s.`target_position` = old_s.`target_position`
WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND new_s.`created_at` IN ('2026-06-16 14:00:00', '2026-06-15 10:00:00', '2026-06-14 16:10:00', '2026-06-12 15:30:00');

-- b) 对于没有被删除的旧 4 月 session (说明 6 月 session 尚不存在)，直接迁移其时间
UPDATE `interview_session` old_s
JOIN `user` u ON old_s.`user_id` = u.`id` AND u.`username` = 'demo'
SET old_s.`created_at` = CASE old_s.`created_at`
    WHEN '2026-04-23 14:00:00' THEN '2026-06-16 14:00:00'
    WHEN '2026-04-22 10:00:00' THEN '2026-06-15 10:00:00'
    WHEN '2026-04-20 16:10:00' THEN '2026-06-14 16:10:00'
    WHEN '2026-04-18 15:30:00' THEN '2026-06-12 15:30:00'
END
WHERE old_s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00');

-- === BEGIN DEMO SEED DATA ===

-- 1. DELETE EXISTING DEMO SESSIONS IN THIS TIME RANGE
DELETE uw FROM `user_weakness` uw
JOIN `interview_session` s ON uw.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo' AND s.`created_at` IN ('2026-06-11 10:00:00', '2026-06-12 11:00:00', '2026-06-13 14:00:00', '2026-06-14 16:00:00', '2026-06-15 15:00:00', '2026-06-16 10:00:00', '2026-06-16 11:30:00');

DELETE sh FROM `score_history` sh
JOIN `interview_session` s ON sh.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo' AND s.`created_at` IN ('2026-06-11 10:00:00', '2026-06-12 11:00:00', '2026-06-13 14:00:00', '2026-06-14 16:00:00', '2026-06-15 15:00:00', '2026-06-16 10:00:00', '2026-06-16 11:30:00');

DELETE im FROM `interview_message` im
JOIN `interview_session` s ON im.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo' AND s.`created_at` IN ('2026-06-11 10:00:00', '2026-06-12 11:00:00', '2026-06-13 14:00:00', '2026-06-14 16:00:00', '2026-06-15 15:00:00', '2026-06-16 10:00:00', '2026-06-16 11:30:00');

DELETE st FROM `interview_stage` st
JOIN `interview_session` s ON st.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo' AND s.`created_at` IN ('2026-06-11 10:00:00', '2026-06-12 11:00:00', '2026-06-13 14:00:00', '2026-06-14 16:00:00', '2026-06-15 15:00:00', '2026-06-16 10:00:00', '2026-06-16 11:30:00');

DELETE s FROM `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo' AND s.`created_at` IN ('2026-06-11 10:00:00', '2026-06-12 11:00:00', '2026-06-13 14:00:00', '2026-06-14 16:00:00', '2026-06-15 15:00:00', '2026-06-16 10:00:00', '2026-06-16 11:30:00');

-- 2. INSERT INTERVIEW SESSIONS

-- ----------------------------
-- 清理旧的 Demo 会话数据（保证幂等重置）
-- ----------------------------
DELETE FROM `user_weakness` WHERE `session_id` IN (SELECT `id` FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo'));
DELETE FROM `score_history` WHERE `session_id` IN (SELECT `id` FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo'));
DELETE FROM `interview_stage` WHERE `session_id` IN (SELECT `id` FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo'));
DELETE FROM `interview_message` WHERE `session_id` IN (SELECT `id` FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo'));
DELETE FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo');

-- ----------------------------
-- 重建真实的 Demo 会话数据
-- ----------------------------

-- Session S1
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, 'Java 后端工程师', 'deepseek', 'deepseek-v4-pro', 'finished', NULL, '## 面试概览\n本场围绕 Spring 事务、评分写入和报告生成失败恢复展开。候选人能够意识到面试消息、三维评分和薄弱点属于同一次业务结果，应该在数据库写入阶段保持一致；同时也能指出大模型调用不应放在事务中，避免长事务占用连接。整体表现说明候选人具备基本后端工程意识，但对事务传播、失败补偿和状态机落地仍偏概括。\n## 表现亮点\n候选人能区分外部模型调用和本地数据库事务，能够说出吞异常可能导致事务不回滚，也能考虑报告生成失败时不应丢失用户回答。这些判断与 Prelude 的会话保存、评分写入、报告生成链路比较贴合。\n## 主要薄弱点\n回答中对传播行为、代理边界、rollback-only、重试表设计等细节展开不足。失败恢复更多停留在“提示重试”的层面，对后端如何记录失败状态、如何避免重复写评分还缺少明确方案。\n## 下一步练习建议\n建议重点练习 Spring 事务失效场景、条件更新、补偿任务和幂等写入。可以用“保存消息、写入评分、生成报告”作为完整案例，画出状态流转和异常分支，再说明每个分支如何保证数据一致。', '2026-06-11 10:00:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '我们从你项目里的评分写入场景开始。假设一次面试结束后，需要保存消息、三维评分和薄弱点，你会把这些操作放在一个事务里吗？', 1, NULL, NULL, '2026-06-11 10:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会倾向于放在一个事务里，因为这些数据表达的是同一次面试结果。如果消息已经保存了，但是评分或薄弱点没有保存，数据看板就会出现缺口。不过我也会注意不要把大模型调用放进事务里，只把数据库写入放进去。', 2, 6, '能区分模型调用和数据库写入，但事务边界还可以更精确。', '2026-06-11 10:00:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '为什么不把大模型调用也放在事务里面？这样不是更完整吗？', 3, NULL, NULL, '2026-06-11 10:00:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '大模型调用耗时不稳定，而且可能失败。如果事务一直占着数据库连接，会影响并发。更合理的是先调用模型，拿到结构化结果后再进入事务写库。如果写库失败，可以把失败状态记录下来，后续重试。', 4, 6, '能说明外部调用不应占用事务，恢复策略还可补充。', '2026-06-11 10:00:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果事务方法里捕获了异常但没有抛出，会发生什么？', 5, NULL, NULL, '2026-06-11 10:00:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '默认情况下事务可能不会回滚，因为框架看不到异常。我会避免在事务方法里吞异常。如果必须捕获，就记录日志后重新抛出业务异常，或者显式标记 rollback-only。', 6, 6, '能说出吞异常导致不回滚，属于基础正确。', '2026-06-11 10:00:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果消息写入成功，评分写入失败，用户刷新页面时看到什么会比较合理？', 7, NULL, NULL, '2026-06-11 10:00:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '用户至少应该能看到已经完成的面试消息，但页面不能假装评分已经生成。我会让会话停在报告生成失败或待重试状态，并给出重新生成报告的入口。', 8, 5, '能考虑用户可见状态，但失败状态设计还不够细。', '2026-06-11 10:00:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '这个回答基本覆盖了事务边界和失败恢复。最后说一下你觉得这里最容易踩的坑是什么。', 9, NULL, NULL, '2026-06-11 10:00:08'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我觉得最容易把外部调用和数据库事务混在一起，或者失败后只在前端隐藏问题。这样短期看起来没报错，但数据看板和报告会慢慢不一致。', 10, 5, '能指出数据一致性风险，但缺少具体落地方案。', '2026-06-11 10:00:09'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;

INSERT INTO `score_history` (`user_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`)
SELECT `user_id`, `id`, 5, 6, 4, '2026-06-11 10:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;
INSERT INTO `user_weakness` (`user_id`, `session_id`, `category`, `description`, `created_at`)
SELECT `user_id`, `id`, '事务边界', '对事务边界和失败状态有基本理解，但对重试、补偿和状态机的落地设计还不够完整。', '2026-06-11 10:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-11 10:00:00' LIMIT 1;

-- Session S2
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, '前端工程师', 'deepseek', 'deepseek-v4-pro', 'finished', NULL, '## 面试概览\n本场围绕输入区元信息控件、模型下拉、会话开始后的只读状态和浮层统一展开。候选人能够识别简历、岗位、模型、JD 控件应共享高度、字体、圆角和浮层风格，也能把 composer 中的模型切换与设置页的完整 LLM 配置区分开。整体表现具备较好的 UI 工程意识。\n## 表现亮点\n候选人能从组件职责出发解释为什么业务组件不应各写一套 class，也能说明会话开始后简历和岗位应变为只读，避免上下文漂移。对 Select、Dropdown、Tooltip 这类低浮层视觉的统一也有明确认知。\n## 主要薄弱点\n回答中对 loading、disabled、focus-visible、long text、narrow width 等状态覆盖提及不足。模型切换“从下一轮生效”的描述合理，但还需要进一步说明前端如何提示用户以及后端如何保存当前模型。\n## 下一步练习建议\n建议继续练习组件 API 设计和状态矩阵审查。可以围绕 composer 写出 trigger、dropdown、empty、disabled、error、focus 六类状态，并说明哪些样式属于 primitive，哪些属于业务组件。', '2026-06-12 11:00:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '我们看一个前端工作台场景。输入框下方有简历、岗位、模型和 JD 几个控件，你会怎么避免它们样式越来越不一致？', 1, NULL, NULL, '2026-06-12 11:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会先把它们都当成同一类元信息控件处理，比如统一高度、字号、圆角和浮层样式。业务组件只传入内容和状态，不在每个组件里单独写一套 class。', 2, 7, '能抓住控件统一的核心，但可以补充状态覆盖。', '2026-06-12 11:00:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果模型下拉既要能切换模型，又要能进入 LLM 配置页，你会怎么组织交互？', 3, NULL, NULL, '2026-06-12 11:00:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会让下拉列表只切当前 provider 下的模型，底部放一个进入 LLM 配置的入口。这样用户在输入区不会误切 provider，也能找到完整配置。', 4, 7, '模型入口设计清楚，边界感较好。', '2026-06-12 11:00:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果会话已经开始，简历和岗位还能不能改？', 5, NULL, NULL, '2026-06-12 11:00:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我认为不能改。开始后它们应该变成只读展示，因为继续修改会影响当前会话的上下文。模型可以在一定范围内切换，但也需要明确是从下一轮开始生效。', 6, 6, '能说明开始后只读，但模型切换生效时机还需更明确。', '2026-06-12 11:00:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '你提到统一浮层，具体到 Tooltip、Select、Dropdown，会注意什么？', 7, NULL, NULL, '2026-06-12 11:00:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会让它们共用 surface、弱阴影、弱边界和统一 padding。不要有的下拉很重，有的又贴着触发器，也不要用原生 title。', 8, 7, '能覆盖浮层视觉规范。', '2026-06-12 11:00:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果暗色主题下某个下拉和背景融在一起，你怎么排查？', 9, NULL, NULL, '2026-06-12 11:00:08'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会先查它是否走了 token。如果业务组件写了自己的背景或阴影，就收敛回 primitive。然后看 hover、selected、disabled 状态是不是都有暗色 token。', 10, 6, '排查路径合理，但验证方式可以再具体。', '2026-06-12 11:00:09'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;

INSERT INTO `score_history` (`user_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`)
SELECT `user_id`, `id`, 6, 7, 5, '2026-06-12 11:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;
INSERT INTO `user_weakness` (`user_id`, `session_id`, `category`, `description`, `created_at`)
SELECT `user_id`, `id`, '前端组件一致性', '对 UI 收敛方向判断正确，但对状态覆盖和验证手段还可以更系统。', '2026-06-12 11:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-12 11:00:00' LIMIT 1;

-- Session S3
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, '算法工程师', 'deepseek', 'deepseek-v4-pro', 'finished', NULL, '## 面试概览\n本场围绕简历与岗位匹配评分展开，重点讨论规则、LLM 辅助、关键词证据、分数解释和冲突处理。候选人没有盲目选择复杂模型，而是根据数据量较小的项目环境提出规则加 LLM 的方案，符合 Prelude 当前的产品阶段。\n## 表现亮点\n候选人能够区分关键词出现和真实项目经验之间的差异，也能把匹配分拆成技能匹配、项目相关性和表达完整度。这种解释方式有助于用户理解简历诊断结果，而不是只看到一个不可解释的总分。\n## 主要薄弱点\n候选人尚未给出明确权重、阈值和置信度计算方式。对于规则分和 LLM 判断冲突时的处理，虽然提出降低自动置信度，但缺少更具体的审核或回退机制。\n## 下一步练习建议\n建议补充一套可解释评分表，例如核心技能、项目证据、岗位职责覆盖、表达完整度分别占多少权重。再设计冲突处理策略：何时采用规则结果，何时采纳 LLM 补充，何时提示用户人工确认。', '2026-06-13 14:00:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = '算法工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '项目里如果要做简历和岗位的匹配评分，你会先用复杂模型还是先用规则？', 1, NULL, NULL, '2026-06-13 14:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '如果数据量不大，我会先用规则和 LLM 辅助。比如先抽取技能、项目经验、年限和岗位关键词，再让模型判断描述是否真的匹配。直接训练复杂模型可能数据不够。', 2, 7, '能根据数据量选择规则加 LLM，方案稳妥。', '2026-06-13 14:00:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果 JD 里写了 Spring Boot，但简历里只写了 Java Web 项目，你会直接判不匹配吗？', 3, NULL, NULL, '2026-06-13 14:00:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '不会直接判不匹配。我会看项目描述里有没有接口设计、数据库、事务、部署这些信号。如果这些内容能支持后端经验，可以给部分分，但会提示 Spring Boot 相关表述不足。', 4, 6, '能避免机械关键词匹配，但评估标准还可量化。', '2026-06-13 14:00:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '匹配分数应该怎么解释给用户？', 5, NULL, NULL, '2026-06-13 14:00:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会把总分拆成技能匹配、项目相关性和表达完整度。用户不只看到一个分数，还能知道是缺关键词，还是项目经历写得不够具体。', 6, 7, '能把总分拆成可解释维度。', '2026-06-13 14:00:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果岗位关键词很多，怎么避免关键词堆砌导致误判？', 7, NULL, NULL, '2026-06-13 14:00:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '可以给关键词分层。核心技能权重更高，普通加分项权重低一些。还要结合上下文判断，比如只在技能列表里出现一次和在项目里实际使用过，分数不应该一样。', 8, 8, '能区分关键词层级和上下文证据。', '2026-06-13 14:00:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果 LLM 输出和规则分数冲突，你会怎么处理？', 9, NULL, NULL, '2026-06-13 14:00:08'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会先保留规则分作为可解释底线，再让 LLM 给出补充判断。如果差异很大，就降低自动置信度，展示需要用户确认的建议，而不是直接覆盖规则结果。', 10, 7, '能处理规则与 LLM 冲突，但置信度策略还可细化。', '2026-06-13 14:00:09'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;

INSERT INTO `score_history` (`user_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`)
SELECT `user_id`, `id`, 7, 6, 8, '2026-06-13 14:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;
INSERT INTO `user_weakness` (`user_id`, `session_id`, `category`, `description`, `created_at`)
SELECT `user_id`, `id`, '匹配评分解释', '能提出可解释匹配思路，但权重、置信度和冲突处理还需要更明确的规则。', '2026-06-13 14:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '算法工程师' AND `created_at` = '2026-06-13 14:00:00' LIMIT 1;

-- Session S4
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, 'Java 后端工程师', 'deepseek', 'deepseek-v4-pro', 'finished', NULL, '## 面试概览\n本场讨论报告生成请求的幂等控制、状态更新、模型调用失败和报告存储边界。候选人能围绕 session id、状态流转和条件更新组织回答，说明其对后端并发场景有较清晰的认识。整体回答比前几场更具体，能够落到数据库条件更新和结果恢复。\n## 表现亮点\n候选人提出先将 ongoing 条件更新为 generating，只有更新成功的请求才能继续生成报告。这一方案能有效避免重复请求造成多任务并发。候选人也能区分 interview_message 作为过程数据和 summary_report 作为结果数据。\n## 主要薄弱点\n任务状态持久化仍不够完整。比如模型原始输出是否保存、失败次数如何记录、重试是否需要幂等键、用户刷新时如何呈现失败状态，这些问题还可以进一步结构化。\n## 下一步练习建议\n建议用状态机方式重画报告生成链路：ongoing、generating、finished、failed 四类状态如何流转，每个状态允许哪些接口动作。再补充条件更新 SQL、失败重试和重复请求返回策略。', '2026-06-14 16:00:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果用户点击生成报告后网络超时，前端又发起了一次生成请求，后端怎么避免重复生成？', 1, NULL, NULL, '2026-06-14 16:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会用 session id 做生成任务的边界。生成前先检查会话状态，如果已经是生成中或已完成，就不要重复创建任务，而是返回当前状态。', 2, 7, '能从会话状态控制重复生成。', '2026-06-14 16:00:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果第一次请求已经开始调用模型，但状态还没写成生成中，第二次请求进来会怎样？', 3, NULL, NULL, '2026-06-14 16:00:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '这里需要在状态更新上做原子处理。比如先在数据库里把 ongoing 更新为 generating，并带上条件 where status = ongoing。只有更新成功的请求才能继续生成。', 4, 8, '能提出条件更新控制并发，落地性较好。', '2026-06-14 16:00:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果模型生成成功，但写报告失败，下一次重试应该从哪里开始？', 5, NULL, NULL, '2026-06-14 16:00:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '可以重新生成，但更好的是保存模型原始输出或生成任务状态。如果没有保存，就只能重新调用模型。至少要保证失败时 session 仍处于可重试状态，而不是直接 finished。', 6, 7, '能考虑失败恢复，但任务状态设计还可展开。', '2026-06-14 16:00:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '你会把报告生成和消息追加放在同一张表里吗？', 7, NULL, NULL, '2026-06-14 16:00:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '不会。消息是对话过程，报告是会话结果。消息表应该追加保存，报告可以放在 session 的 summary_report 或单独报告表里。', 8, 8, '能区分过程数据和结果数据。', '2026-06-14 16:00:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '最后说一下这个流程里你最看重的一个约束。', 9, NULL, NULL, '2026-06-14 16:00:08'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我最看重状态不能乱。用户的回答不能因为报告失败丢失，报告也不能因为重复请求生成两份互相覆盖。', 10, 8, '能抓住用户输入持久化和报告一致性的核心。', '2026-06-14 16:00:09'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;

INSERT INTO `score_history` (`user_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`)
SELECT `user_id`, `id`, 7, 8, 6, '2026-06-14 16:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;
INSERT INTO `user_weakness` (`user_id`, `session_id`, `category`, `description`, `created_at`)
SELECT `user_id`, `id`, '生成任务幂等', '幂等和状态控制思路较好，但对任务状态持久化和失败重试链路还可细化。', '2026-06-14 16:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-14 16:00:00' LIMIT 1;

-- Session S5
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, '前端工程师', 'deepseek', 'deepseek-v4-pro', 'finished', NULL, '## 面试概览\n本场围绕浅色/暗色/跟随系统主题、ECharts、canvas、shader 和 UI primitive 的主题适配展开。候选人能够指出普通 DOM 可以依赖 CSS token，而图表和 canvas 需要在主题切换后主动重新读取颜色并重绘。整体表现与 Prelude 本轮 UI 收敛工作高度相关。\n## 表现亮点\n候选人能提出统一主题工具负责写入根节点状态并派发主题变更事件，业务组件只处理必须重绘的部分。对 ECharts tooltip 内联样式、canvas 初始化颜色、自动化断言等问题有较好意识，回答具体且可执行。\n## 主要薄弱点\n虽然候选人能识别主题风险，但对完整视觉回归矩阵仍可补充。例如登录页、设置弹窗、下拉浮层、Toast、Dialog、Analytics 图表在暗色下的对比度需要形成固定 smoke 清单。\n## 下一步练习建议\n建议继续整理主题切换测试策略：哪些组件只需 CSS token，哪些组件需要监听主题事件，哪些场景需要截图或 canvas 差异断言。对 UI primitive 的 hover、disabled、selected 状态也应纳入暗色验收。', '2026-06-15 15:00:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果应用支持浅色、暗色和跟随系统，你觉得最容易漏掉哪些地方？', 1, NULL, NULL, '2026-06-15 15:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '最容易漏掉 canvas、图表和一些第三方组件。普通 DOM 可以靠 CSS token 自动变，但 ECharts 或 shader 可能初始化时读了一次颜色，主题切换后不会自己更新。', 2, 8, '能识别非 DOM 渲染组件的主题风险。', '2026-06-15 15:00:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '那你会怎么设计主题切换事件？', 3, NULL, NULL, '2026-06-15 15:00:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会有一个统一的主题工具，负责更新 html 上的 class 或 data-theme，同时派发一个主题变更事件。需要重绘的组件监听这个事件，然后重新读取 token。', 4, 8, '主题事件设计清楚，边界合理。', '2026-06-15 15:00:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果图表 tooltip 里用了内联 HTML 样式，会有什么问题？', 5, NULL, NULL, '2026-06-15 15:00:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '它可能绕过全局 token，暗色下文字和背景对比不对。应该尽量从 token 解析颜色，再传给图表配置，不要写死颜色。', 6, 7, '能指出 ECharts 内联样式风险。', '2026-06-15 15:00:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '你如何验证暗色主题不是只看起来切了 class？', 7, NULL, NULL, '2026-06-15 15:00:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会检查输入框、下拉、弹窗、图表和 canvas。自动化上可以断言主题事件后图表 canvas 有更新，关键 token 值也发生变化。', 8, 9, '能把视觉检查和自动化断言结合。', '2026-06-15 15:00:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果某个按钮 hover 在暗色下不明显，你会先改业务组件还是 primitive？', 9, NULL, NULL, '2026-06-15 15:00:08'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '如果是通用按钮，就先改 primitive 的 token 和 variant。只有特定业务状态才在业务组件里处理，避免每个页面都写一套 hover。', 10, 8, '能区分 primitive 和业务组件职责。', '2026-06-15 15:00:09'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;

INSERT INTO `score_history` (`user_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`)
SELECT `user_id`, `id`, 8, 7, 9, '2026-06-15 15:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;
INSERT INTO `user_weakness` (`user_id`, `session_id`, `category`, `description`, `created_at`)
SELECT `user_id`, `id`, '主题验证', '暗色主题和图表重绘理解较好，后续可继续补充视觉回归覆盖范围。', '2026-06-15 15:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-15 15:00:00' LIMIT 1;

-- Session S6
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, 'Java 后端工程师', 'deepseek', 'deepseek-v4-pro', 'ongoing', NULL, NULL, '2026-06-16 10:00:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '我们继续看面试系统本身。如果用户连续发送两条回答，同时后台正在准备下一轮追问，你会怎么避免消息顺序错乱？', 1, NULL, NULL, '2026-06-16 10:00:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会让消息写入按 session id 和 seq_num 控制顺序，服务端生成下一个序号，而不是完全相信前端传来的序号。并且同一个 session 的写入要有串行化或条件检查。', 2, 7, '能意识到 seq_num 应由服务端控制。', '2026-06-16 10:00:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果第一条回答已经写入，但模型生成追问失败，界面应该怎么表现？', 3, NULL, NULL, '2026-06-16 10:00:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会保留用户回答，不回滚。面试官位置可以显示生成失败和重试入口。这样用户知道自己的输入没丢，也能继续恢复流程。', 4, 8, '能区分用户输入持久化和模型调用失败。', '2026-06-16 10:00:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果用户这时刷新页面，后端需要返回什么状态？', 5, NULL, NULL, '2026-06-16 10:00:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '后端应该返回已有消息、当前会话状态，以及最后一次模型调用是否失败。前端根据这个状态决定是显示重试，还是继续让用户输入。', 6, 7, '能说明刷新恢复需要返回的状态。', '2026-06-16 10:00:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '这个方向可以。下一步我会追问你 seq_num 并发冲突时的数据库约束怎么设计。', 7, NULL, NULL, '2026-06-16 10:00:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会考虑在 session_id 和 seq_num 上加唯一约束，冲突时重新读取最大序号或直接让同一会话写入走队列。不过这块我还需要结合实际并发量取舍。', 8, 7, '能提出唯一约束和队列思路，但取舍还需展开。', '2026-06-16 10:00:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = 'Java 后端工程师' AND `created_at` = '2026-06-16 10:00:00' LIMIT 1;

-- Session S7
INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, '前端工程师', 'deepseek', 'deepseek-v4-pro', 'ongoing', NULL, NULL, '2026-06-16 11:30:00'
FROM `user` u JOIN `resume` r ON u.`id` = r.`user_id` JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo' LIMIT 1;

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果输入区里有模型下拉，设置弹窗里也有 LLM 配置，你会怎么避免两个入口状态不一致？', 1, NULL, NULL, '2026-06-16 11:30:00'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '我会让 composer 只切当前 provider 下的模型，完整 provider 和 key 配置放在设置里。保存设置后同步当前模型状态，composer 不直接管理 provider。', 2, 7, '能区分 composer 和设置页职责。', '2026-06-16 11:30:01'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果用户打开模型下拉时，provider 还没有可选模型列表怎么办？', 3, NULL, NULL, '2026-06-16 11:30:02'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '可以显示当前模型和一个进入 LLM 配置的入口，不要硬塞空列表。必要时提示去设置页检测模型。', 4, 6, '空状态处理合理，但可以补充加载和错误状态。', '2026-06-16 11:30:03'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '会话进行中还能切模型吗？', 5, NULL, NULL, '2026-06-16 11:30:04'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '可以允许切当前 provider 的模型，但要明确它影响后续回答，不改变已经生成的历史消息。UI 上也不能让用户误以为会重算已有内容。', 6, 7, '能说明模型切换只影响后续消息。', '2026-06-16 11:30:05'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'assistant', '如果下拉宽度比触发器宽一点，你会把样式写在业务组件里吗？', 7, NULL, NULL, '2026-06-16 11:30:06'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT `id`, 'user', '如果是所有 dropdown 都应该对齐，就应该改共享浮层样式。如果只有模型下拉特殊，再在这个业务组件里用 anchor width 约束，但不要改成硬编码宽度。', 8, 7, '能区分共享样式和业务约束。', '2026-06-16 11:30:07'
FROM `interview_session` WHERE `user_id` = (SELECT `id` FROM `user` WHERE `username` = 'demo') AND `target_position` = '前端工程师' AND `created_at` = '2026-06-16 11:30:00' LIMIT 1;
