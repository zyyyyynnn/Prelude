-- 生产安全的参考数据与历史值维护。脚本必须保持幂等，不得包含 demo 用户或会话。
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

-- 旧协议值直接归并到明确协议；用户根地址与加密 Key 保持原值。
UPDATE `user`
SET `llm_base_url` = CASE
        WHEN `llm_provider` = 'openai' AND (`llm_base_url` IS NULL OR `llm_base_url` = '')
            THEN 'https://api.openai.com/v1'
        ELSE `llm_base_url`
    END,
    `llm_provider` = 'openai-chat-completions'
WHERE `llm_provider` IN ('openai', 'openai-compatible');

UPDATE `user`
SET `llm_base_url` = CASE
        WHEN `llm_base_url` IS NULL OR `llm_base_url` = '' THEN 'https://api.anthropic.com/v1'
        ELSE `llm_base_url`
    END,
    `llm_provider` = 'anthropic-messages'
WHERE `llm_provider` = 'anthropic';

UPDATE `interview_session`
SET `llm_provider` = 'openai-chat-completions'
WHERE `llm_provider` IN ('openai', 'openai-compatible');

UPDATE `interview_session`
SET `llm_provider` = 'anthropic-messages'
WHERE `llm_provider` = 'anthropic';

DELETE FROM `llm_provider_config`
WHERE `provider_key` IN ('openai', 'openai-compatible', 'anthropic');

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'openai-responses', 'OpenAI Responses', '', '[]', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'openai-responses'
);

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'openai-chat-completions', 'OpenAI Chat Completions', '', '[]', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'openai-chat-completions'
);

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'anthropic-messages', 'Anthropic Messages', '', '[]', 1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'anthropic-messages'
);
