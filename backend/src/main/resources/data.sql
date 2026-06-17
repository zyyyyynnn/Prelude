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

-- 4. Session UPDATE + INSERT\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'finished',
    s.`summary` = '候选人能完成基本的 CSS 布局和盒模型说明，但对 BFC 细节不够深入。',
    s.`summary_report` = '# 面试评估报告\n\n## 结论\n基础知识扎实，但原理层面需要加强。\n\n## 维度评分\n- 技术能力：5.8/10\n- 表达清晰度：6.1/10\n- 逻辑思维：5.9/10\n'
WHERE s.`created_at` = '2026-06-12 10:00:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished', '候选人能完成基本的 CSS 布局和盒模型说明，但对 BFC 细节不够深入。', '# 面试评估报告\n\n## 结论\n基础知识扎实，但原理层面需要加强。\n\n## 维度评分\n- 技术能力：5.8/10\n- 表达清晰度：6.1/10\n- 逻辑思维：5.9/10\n', '2026-06-12 10:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-12 10:00:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是一名前端面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '请解释一下 CSS 的盒模型机制。', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '盒模型包括 margin、border、padding 和 content。有标准盒模型和 IE 盒模型，可以通过 box-sizing 切换。', 2, 6.0, '基本概念清晰。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '那么什么是 BFC，它主要解决什么问题？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', 'BFC 是块级格式化上下文。主要是用来解决 margin 塌陷的问题。', 4, 5.5, '只答出了一部分应用场景，没有说清 BFC 的隔离机制和触发条件。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `interview_stage` (`session_id`, `stage_name`, `seq_num`, `score`, `summary`) SELECT s.`id`, 'deep_dive', 1, 5.8, '深入讨论' FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `user_weakness` (`user_id`, `session_id`, `weakness_type`, `description`, `created_at`) SELECT s.`user_id`, s.`id`, 'Tech', '底层原理理解有待加深', TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\nINSERT INTO `score_history` (`session_id`, `user_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`) SELECT s.`id`, s.`user_id`, 5.8, 6.1, 5.9, TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-12 10:00:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '推荐算法工程师.pdf'
JOIN `position_template` p ON p.`name` = '算法工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'finished',
    s.`summary` = '候选人能写出快速排序，但复杂度分析略显吃力。',
    s.`summary_report` = '# 面试评估报告\n\n## 结论\n算法基础尚可，复杂度分析需练习。\n\n## 维度评分\n- 技术能力：6.2/10\n- 表达清晰度：6.4/10\n- 逻辑思维：6.3/10\n'
WHERE s.`created_at` = '2026-06-13 14:00:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished', '候选人能写出快速排序，但复杂度分析略显吃力。', '# 面试评估报告\n\n## 结论\n算法基础尚可，复杂度分析需练习。\n\n## 维度评分\n- 技术能力：6.2/10\n- 表达清晰度：6.4/10\n- 逻辑思维：6.3/10\n', '2026-06-13 14:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '推荐算法工程师.pdf'
JOIN `position_template` p ON p.`name` = '算法工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-13 14:00:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是算法工程师面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '能写一个快速排序吗，并说明时间复杂度。', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '（给出了快速排序的代码）平均时间复杂度是 O(NlogN)，最坏是 O(N^2)。', 2, 6.5, '代码没问题，复杂度结论正确。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '最坏情况在什么时候发生？如何避免？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '数组倒序的时候。可以随机选 pivot 避免。', 4, 6.0, '有些简略，不过点到了。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `interview_stage` (`session_id`, `stage_name`, `seq_num`, `score`, `summary`) SELECT s.`id`, 'deep_dive', 1, 6.2, '深入讨论' FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `user_weakness` (`user_id`, `session_id`, `weakness_type`, `description`, `created_at`) SELECT s.`user_id`, s.`id`, 'Tech', '底层原理理解有待加深', TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\nINSERT INTO `score_history` (`session_id`, `user_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`) SELECT s.`id`, s.`user_id`, 6.2, 6.4, 6.3, TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-13 14:00:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'finished',
    s.`summary` = '候选人熟悉 Spring Boot 和基本 MySQL 优化，但分库分表经验不足。',
    s.`summary_report` = '# 面试评估报告\n\n## 结论\n业务开发能力较好，架构经验待积累。\n\n## 维度评分\n- 技术能力：6.9/10\n- 表达清晰度：7.0/10\n- 逻辑思维：6.9/10\n'
WHERE s.`created_at` = '2026-06-14 16:10:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished', '候选人熟悉 Spring Boot 和基本 MySQL 优化，但分库分表经验不足。', '# 面试评估报告\n\n## 结论\n业务开发能力较好，架构经验待积累。\n\n## 维度评分\n- 技术能力：6.9/10\n- 表达清晰度：7.0/10\n- 逻辑思维：6.9/10\n', '2026-06-14 16:10:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-14 16:10:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是 Java 面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', 'MySQL 的聚簇索引和非聚簇索引有什么区别？', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '聚簇索引的叶子节点存了整行数据，一般是主键；非聚簇索引存的是主键的值。', 2, 7.0, '概念准确。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '什么情况下会发生回表？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '通过非聚簇索引查数据时，如果 select 的列不在索引里，就要拿主键去聚簇索引再查一次，这就是回表。', 4, 7.0, '解释清晰。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `interview_stage` (`session_id`, `stage_name`, `seq_num`, `score`, `summary`) SELECT s.`id`, 'deep_dive', 1, 6.9, '深入讨论' FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `user_weakness` (`user_id`, `session_id`, `weakness_type`, `description`, `created_at`) SELECT s.`user_id`, s.`id`, 'Tech', '底层原理理解有待加深', TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\nINSERT INTO `score_history` (`session_id`, `user_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`) SELECT s.`id`, s.`user_id`, 6.9, 7.0, 6.9, TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-14 16:10:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'finished',
    s.`summary` = '候选人对 Vue 和 React 的组件设计有较深理解，状态管理方案合理。',
    s.`summary_report` = '# 面试评估报告\n\n## 结论\n前端框架底层机制掌握良好，工程化经验丰富。\n\n## 维度评分\n- 技术能力：7.2/10\n- 表达清晰度：7.4/10\n- 逻辑思维：7.3/10\n'
WHERE s.`created_at` = '2026-06-15 10:00:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished', '候选人对 Vue 和 React 的组件设计有较深理解，状态管理方案合理。', '# 面试评估报告\n\n## 结论\n前端框架底层机制掌握良好，工程化经验丰富。\n\n## 维度评分\n- 技术能力：7.2/10\n- 表达清晰度：7.4/10\n- 逻辑思维：7.3/10\n', '2026-06-15 10:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-15 10:00:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是前端面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', 'React 的 useEffect 和 Vue 的 watch 有什么本质区别？', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', 'useEffect 是副作用同步机制，依赖变化后执行；watch 是对响应式数据的监听。', 2, 7.0, '把握了基本区别。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '如果在 useEffect 里没有写依赖数组，会发生什么？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '组件每次 render 之后都会执行。', 4, 7.5, '正确。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `interview_stage` (`session_id`, `stage_name`, `seq_num`, `score`, `summary`) SELECT s.`id`, 'deep_dive', 1, 7.2, '深入讨论' FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `user_weakness` (`user_id`, `session_id`, `weakness_type`, `description`, `created_at`) SELECT s.`user_id`, s.`id`, 'Tech', '底层原理理解有待加深', TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\nINSERT INTO `score_history` (`session_id`, `user_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`) SELECT s.`id`, s.`user_id`, 7.2, 7.4, 7.3, TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-15 10:00:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'finished',
    s.`summary` = '候选人在高并发秒杀、消息最终一致性和架构降级方面表达稳定。',
    s.`summary_report` = '# 面试评估报告\n\n## 结论\n具备很强的高并发和分布式事务处理能力。\n\n## 维度评分\n- 技术能力：8.0/10\n- 表达清晰度：8.2/10\n- 逻辑思维：7.9/10\n'
WHERE s.`created_at` = '2026-06-16 14:00:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished', '候选人在高并发秒杀、消息最终一致性和架构降级方面表达稳定。', '# 面试评估报告\n\n## 结论\n具备很强的高并发和分布式事务处理能力。\n\n## 维度评分\n- 技术能力：8.0/10\n- 表达清晰度：8.2/10\n- 逻辑思维：7.9/10\n', '2026-06-16 14:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-16 14:00:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是 Java 后端高阶面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '如果让你设计一个秒杀系统，如何保证不超卖？', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '我会用 Redis 的 Lua 脚本进行库存预扣减，保证原子性，扣减成功再发 MQ 给下游生成订单。', 2, 8.0, '抓住了原子性和异步削峰。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', 'Redis 挂了怎么办？库存数据怎么保证一致？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '可以用 MySQL 的乐观锁作为最终防线，更新时带上 version 或者条件 stock > 0。', 4, 8.2, '有明确的兜底手段。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `interview_stage` (`session_id`, `stage_name`, `seq_num`, `score`, `summary`) SELECT s.`id`, 'deep_dive', 1, 8.0, '深入讨论' FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `user_weakness` (`user_id`, `session_id`, `weakness_type`, `description`, `created_at`) SELECT s.`user_id`, s.`id`, 'Tech', '底层原理理解有待加深', TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\nINSERT INTO `score_history` (`session_id`, `user_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`) SELECT s.`id`, s.`user_id`, 8.0, 8.2, 7.9, TIMESTAMPADD(MINUTE, 30, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-16 14:00:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'ongoing',
    s.`summary` = '正在讨论事务边界、消息写入和报告生成失败恢复。',
    s.`summary_report` = NULL
WHERE s.`created_at` = '2026-06-17 09:00:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'ongoing', '正在讨论事务边界、消息写入和报告生成失败恢复。', NULL, '2026-06-17 09:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-17 09:00:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是 Java 资深后端面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '我们先从最近一个后端项目聊起。请你介绍一下项目背景、你负责的模块，以及这个模块解决了什么问题。', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '我最近做的是一个模拟面试和简历诊断系统，后端主要负责用户会话、简历解析结果、面试记录和评分数据的管理。我负责的模块偏业务闭环，包括创建面试会话、保存多轮问答、生成阶段性评分，以及把历史数据汇总到数据看板。这个模块解决的问题是让用户每次练习后都能留下可追踪的记录，而不是只得到一次性的聊天结果。', 2, 7.0, '项目职责描述清楚，但可以补充一个具体接口或数据表设计例子。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '如果同一个用户连续发送多条回答，同时报告生成任务也在后台执行，你会怎么避免会话状态被写乱？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '我会先把状态拆清楚，比如进行中、报告生成中、已完成不能混用。写入回答时只允许进行中会话追加消息，生成报告时基于稳定的 session id 读取当时已有的消息快照。消息表只追加不覆盖，服务层控制状态流转，避免前后端看到的状态不一致。', 4, 7.5, '状态流转和追加写入思路合理，可以进一步说明并发控制手段。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '这个思路可以。下一步我会追问你接口幂等和异常恢复的设计。', 5, NULL, NULL, TIMESTAMPADD(MINUTE, 5, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 09:00:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'ongoing',
    s.`summary` = '正在讨论前端 UI 框架中的状态管理、组件抽象、下拉浮层一致性与暗色主题适配。',
    s.`summary_report` = NULL
WHERE s.`created_at` = '2026-06-17 10:30:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'ongoing', '正在讨论前端 UI 框架中的状态管理、组件抽象、下拉浮层一致性与暗色主题适配。', NULL, '2026-06-17 10:30:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-17 10:30:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是前端面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '如果在项目中要做暗色主题的完全重构，你觉得最大难点在哪，你会怎么做？', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '最大难点在历史组件里散落着大量的硬编码颜色。我会先收敛 token，把所有颜色抽离到 index.css，然后再去处理业务组件。', 2, 7.5, '思路正确，抓住了变量收敛。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '如果有些组件比如 ECharts 画布，或者 Canvas 渲染的 Shader 不直接支持 CSS 变量，你怎么让它跟随主题切换？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '我会监听主题切换事件，通过 getComputedStyle 获取计算后的真实色值，或者手动管理一套 JS 对应的 Palette 传给 Canvas 重绘。', 4, 8.0, '对第三方库和 Canvas 渲染的主题响应有准确认知。', TIMESTAMPADD(MINUTE, 4, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '不错。关于下拉浮层一致性的问题，如果有多个全局 z-index，你会怎么治理？', 5, NULL, NULL, TIMESTAMPADD(MINUTE, 5, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 10:30:00';\n
UPDATE `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo'
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '推荐算法工程师.pdf'
JOIN `position_template` p ON p.`name` = '算法工程师'
SET s.`resume_id` = r.`id`,
    s.`position_id` = p.`id`,
    s.`target_position` = p.`name`,
    s.`llm_provider` = 'deepseek',
    s.`llm_model` = 'deepseek-v4-pro',
    s.`status` = 'ongoing',
    s.`summary` = '正在讨论简历关键词匹配、岗位匹配评分以及何时使用大模型与规则引擎。',
    s.`summary_report` = NULL
WHERE s.`created_at` = '2026-06-17 11:15:00';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'ongoing', '正在讨论简历关键词匹配、岗位匹配评分以及何时使用大模型与规则引擎。', NULL, '2026-06-17 11:15:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '推荐算法工程师.pdf'
JOIN `position_template` p ON p.`name` = '算法工程师'
WHERE u.`username` = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM `interview_session` s2 
      WHERE s2.`user_id` = u.`id` AND s2.`created_at` = '2026-06-17 11:15:00'
  );
\n
DELETE uw FROM `user_weakness` uw JOIN `interview_session` s ON uw.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';
DELETE sh FROM `score_history` sh JOIN `interview_session` s ON sh.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';
DELETE st FROM `interview_stage` st JOIN `interview_session` s ON st.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';
DELETE m FROM `interview_message` m JOIN `interview_session` s ON m.`session_id` = s.`id` JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';
\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'system', '你是推荐算法面试官...', 0, NULL, NULL, TIMESTAMPADD(MINUTE, 0, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '在简历解析和岗位匹配这个场景中，你会选择纯 LLM 方案，还是传统模型+规则的方案？', 1, NULL, NULL, TIMESTAMPADD(MINUTE, 1, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'user', '在数据量比较小时，我倾向于更适合使用规则提取硬性条件，用 LLM 作为辅助进行语义理解和评分，而不是过度吹嘘纯端到端大模型的表现。', 2, 7.5, '工程权衡感较强，没有盲目追求复杂模型。', TIMESTAMPADD(MINUTE, 2, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';\nINSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`) SELECT s.`id`, 'assistant', '如果让你设计一个简历和岗位匹配的打分公式，里面包含关键词覆盖率、工作年限和能力匹配度，你会怎么确定这几项的权重？', 3, NULL, NULL, TIMESTAMPADD(MINUTE, 3, s.`created_at`) FROM `interview_session` s JOIN `user` u ON s.`user_id` = u.`id` AND u.`username` = 'demo' WHERE s.`created_at` = '2026-06-17 11:15:00';