INSERT INTO `user` (`username`, `password`, `email`)
SELECT 'demo',
       '$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2',
       'demo@example.com'
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE `username` = 'demo'
);

INSERT INTO `position_template` (`name`, `system_prompt`)
SELECT 'Java 后端工程师',
       '你是一名严谨的 Java 后端面试官，请重点考察候选人在 Spring Boot、JVM、MySQL 与分布式系统方面的基础与实践能力。提问要循序渐进，注重项目经历追问。'
WHERE NOT EXISTS (
    SELECT 1 FROM `position_template` WHERE `name` = 'Java 后端工程师'
);

INSERT INTO `position_template` (`name`, `system_prompt`)
SELECT '前端工程师',
       '你是一名前端工程师岗位的面试官，请重点考察候选人在 Vue、React、浏览器原理、工程化和交互实现方面的理解。提问风格客观直接，注重场景化追问。'
WHERE NOT EXISTS (
    SELECT 1 FROM `position_template` WHERE `name` = '前端工程师'
);

INSERT INTO `position_template` (`name`, `system_prompt`)
SELECT '算法工程师',
       '你是一名算法工程师岗位的面试官，请重点考察候选人在数据结构、LeetCode 解题思路以及机器学习基础方面的理解与表达。请根据候选人回答逐步加深难度。'
WHERE NOT EXISTS (
    SELECT 1 FROM `position_template` WHERE `name` = '算法工程师'
);

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'deepseek',
       'DeepSeek',
       'https://api.deepseek.com/chat/completions',
       '["deepseek-chat","deepseek-reasoner"]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'deepseek'
);

INSERT INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`)
SELECT 'openai',
       'OpenAI',
       'https://api.openai.com/v1/chat/completions',
       '["gpt-4o","gpt-4o-mini"]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'openai'
);

UPDATE `user`
SET `llm_provider` = COALESCE(`llm_provider`, 'deepseek'),
    `llm_model` = COALESCE(`llm_model`, 'deepseek-chat')
WHERE `llm_provider` IS NULL OR `llm_model` IS NULL;

UPDATE `interview_session`
SET `llm_provider` = COALESCE(`llm_provider`, 'deepseek'),
    `llm_model` = COALESCE(`llm_model`, 'deepseek-chat')
WHERE `llm_provider` IS NULL OR `llm_model` IS NULL;

INSERT INTO `interview_stage` (`session_id`, `stage_name`, `started_at`, `ended_at`)
SELECT s.`id`,
       'warmup',
       s.`created_at`,
       CASE WHEN s.`status` = 'finished' THEN s.`created_at` ELSE NULL END
FROM `interview_session` s
WHERE NOT EXISTS (
    SELECT 1 FROM `interview_stage` st WHERE st.`session_id` = s.`id`
);
