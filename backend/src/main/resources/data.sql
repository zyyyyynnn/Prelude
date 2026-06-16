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
       'OpenAI-compatible',
       '',
       '[]',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM `llm_provider_config` WHERE `provider_key` = 'openai-compatible'
);

-- 3. demo 用户/默认演示会话/消息/评分/报告 seed：仅重建 demo 用户的固定演示数据。
INSERT INTO `user` (`username`, `password`, `email`)
SELECT 'demo',
       '$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2',
       'demo@example.com'
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE `username` = 'demo'
);

INSERT INTO `resume` (`user_id`, `file_name`, `parsed_skills`, `parsed_projects`, `raw_text`, `created_at`)
SELECT u.`id`, 'Java高级架构.pdf', '["Spring Boot","Redis","消息队列","分布式架构"]', '["高并发电商秒杀系统","异步报告生成链路"]', 'Java 后端工程师演示简历。', '2026-04-22 16:40:00'
FROM `user` u
WHERE u.`username` = 'demo'
  AND NOT EXISTS (SELECT 1 FROM `resume` r WHERE r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf');

INSERT INTO `resume` (`user_id`, `file_name`, `parsed_skills`, `parsed_projects`, `raw_text`, `created_at`)
SELECT u.`id`, '大前端资深开发.pdf', '["Vue","React","微前端","性能优化"]', '["微前端平台","虚拟列表优化"]', '前端工程师演示简历。', '2026-04-20 16:10:00'
FROM `user` u
WHERE u.`username` = 'demo'
  AND NOT EXISTS (SELECT 1 FROM `resume` r WHERE r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf');

INSERT INTO `resume` (`user_id`, `file_name`, `parsed_skills`, `parsed_projects`, `raw_text`, `created_at`)
SELECT u.`id`, '推荐算法工程师.pdf', '["推荐系统","机器学习","特征工程","A/B 实验"]', '["推荐排序模型","线上指标诊断"]', '算法工程师演示简历。', '2026-04-18 15:30:00'
FROM `user` u
WHERE u.`username` = 'demo'
  AND NOT EXISTS (SELECT 1 FROM `resume` r WHERE r.`user_id` = u.`id` AND r.`file_name` = '推荐算法工程师.pdf');

DELETE uw
FROM `user_weakness` uw
JOIN `interview_session` s ON uw.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo'
  AND s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND s.`target_position` IN ('Java 后端工程师', '前端工程师', '算法工程师');

DELETE sh
FROM `score_history` sh
JOIN `interview_session` s ON sh.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo'
  AND s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND s.`target_position` IN ('Java 后端工程师', '前端工程师', '算法工程师');

DELETE st
FROM `interview_stage` st
JOIN `interview_session` s ON st.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo'
  AND s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND s.`target_position` IN ('Java 后端工程师', '前端工程师', '算法工程师');

DELETE m
FROM `interview_message` m
JOIN `interview_session` s ON m.`session_id` = s.`id`
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo'
  AND s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND s.`target_position` IN ('Java 后端工程师', '前端工程师', '算法工程师');

DELETE s
FROM `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id`
WHERE u.`username` = 'demo'
  AND s.`created_at` IN ('2026-04-23 14:00:00', '2026-04-22 10:00:00', '2026-04-20 16:10:00', '2026-04-18 15:30:00')
  AND s.`target_position` IN ('Java 后端工程师', '前端工程师', '算法工程师');

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished',
       '候选人能说明接口排查与幂等处理，但部分架构取舍仍偏概括。',
       '# 面试评估报告\n\n## 面试概览\n- 目标岗位：Java 后端工程师\n- 结论：候选人能围绕后端链路说明排查与幂等设计，具备继续深入评估的基础。\n\n## 三维评分\n- 技术能力：7/10\n- 表达清晰度：6/10\n- 逻辑思维：7/10\n\n## 优势总结\n- 能够把慢请求排查拆解到接口耗时、SQL 执行计划和数据量分析\n- 能意识到报告任务需要状态落库、幂等键和通知补偿\n- 对 SSE 断连、广播失败等异步链路问题有基本兜底思路\n\n## 改进建议\n1. 补强事务边界、失败恢复和重试退避的具体设计\n2. 在性能排查中增加监控指标、日志字段和压测数据支撑\n3. 对数据库约束、状态流转和补偿任务的细节说明可以更严谨\n\n## 总结\n整体回答能覆盖核心链路，但工程细节仍偏概括，建议继续补强可观测性和异常场景表达。',
       '2026-04-23 14:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished',
       '候选人对高并发秒杀、消息最终一致性和降级设计有较完整的回答。',
       '# 面试评估报告\n\n## 面试概览\n- 目标岗位：Java 后端工程师\n- 结论：候选人在高并发秒杀、消息最终一致性和架构降级方面表达稳定，具备进入下一轮架构专项面试的基础。\n\n## 三维评分\n- 技术能力：8/10\n- 表达清晰度：9/10\n- 逻辑思维：8/10\n\n## 优势总结\n- 能围绕 Redis Lua、RocketMQ 事务消息和数据库兜底说明秒杀主链路\n- 对 Redis Cluster、MQ 回查、补偿库和核心库保护有较清晰的工程判断\n- 能明确说明一致性与吞吐之间的取舍，表达结构完整\n\n## 改进建议\n1. 补充更多线上容量指标、压测口径和监控阈值\n2. 对库存分片、对账延迟和补偿策略给出更量化的边界\n3. 在架构降级方案中进一步说明用户体验与业务风险控制\n\n## 总结\n整体表现成熟，技术判断和表达都较稳定，适合继续进入更高强度的架构专项评估。',
       '2026-04-22 10:00:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = 'Java高级架构.pdf'
JOIN `position_template` p ON p.`name` = 'Java 后端工程师'
WHERE u.`username` = 'demo';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished',
       '候选人具备前端工程化与性能排查意识，但复杂状态边界还可更清楚。',
       '# 面试评估报告\n\n## 面试概览\n- 目标岗位：前端工程师\n- 结论：候选人具备较完整的前端工程化和页面性能意识，适合继续深入评估。\n\n## 三维评分\n- 技术能力：7/10\n- 表达清晰度：7/10\n- 逻辑思维：7/10\n\n## 优势总结\n- 能够围绕微前端隔离、状态回收和虚拟列表性能说明实现思路\n- 对渲染压力、可见区计算、弱网降级和平台适配有基本判断能力\n- 能将交互细节与真实使用体验关联起来\n\n## 改进建议\n1. 补强浏览器性能指标、资源加载和渲染链路的量化说明\n2. 在复杂组件状态归属和复用边界上给出更清晰的取舍\n3. 对移动端适配、键盘焦点和无障碍状态说明可以更完整\n\n## 总结\n整体表现稳定，具备继续进入前端专项面试的基础。',
       '2026-04-20 16:10:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '大前端资深开发.pdf'
JOIN `position_template` p ON p.`name` = '前端工程师'
WHERE u.`username` = 'demo';

INSERT INTO `interview_session` (`user_id`, `resume_id`, `position_id`, `target_position`, `llm_provider`, `llm_model`, `status`, `summary`, `summary_report`, `created_at`)
SELECT u.`id`, r.`id`, p.`id`, p.`name`, 'deepseek', 'deepseek-v4-pro', 'finished',
       '候选人能覆盖推荐系统主链路，但实验细节与线上诊断仍需加强。',
       '# 面试评估报告\n\n## 面试概览\n- 目标岗位：算法工程师\n- 结论：候选人能按数据、模型和评估链路组织回答，但实验复现和误差分析仍需加强。\n\n## 三维评分\n- 技术能力：6/10\n- 表达清晰度：7/10\n- 逻辑思维：6/10\n\n## 优势总结\n- 能够从样本、特征、基线方案和指标口径拆解问题\n- 对离线评估和线上表现差异有基本排查路径\n- 回答结构较清楚，能说明数据分布变化带来的影响\n\n## 改进建议\n1. 补强时间复杂度、空间复杂度和边界规模的量化表达\n2. 在验证集划分、误差分析和指标选择上给出更具体示例\n3. 对实验版本、参数记录和失败样本复盘说明可以更严谨\n\n## 总结\n整体具备算法岗继续评估的基础，但需要提高实验细节和表达稳定性。',
       '2026-04-18 15:30:00'
FROM `user` u
JOIN `resume` r ON r.`user_id` = u.`id` AND r.`file_name` = '推荐算法工程师.pdf'
JOIN `position_template` p ON p.`name` = '算法工程师'
WHERE u.`username` = 'demo';

INSERT INTO `interview_message` (`session_id`, `role`, `content`, `seq_num`, `score`, `hint`, `created_at`)
SELECT s.`id`, seed.`role`, seed.`content`, seed.`seq_num`, seed.`score`, seed.`hint`, TIMESTAMPADD(MINUTE, seed.`seq_num`, s.`created_at`)
FROM `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id`
JOIN (
    SELECT 'Java 后端工程师' AS target_position, '2026-04-23 14:00:00' AS session_created_at, 0 AS seq_num, 'system' AS role, '你是一名严谨的 Java 后端面试官，请重点考察候选人在 Spring Boot、JVM、MySQL 与分布式系统方面的基础与实践能力。' AS content, NULL AS score, NULL AS hint
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 1, 'assistant', '先从最近一次接口性能排查说起，你会如何定位慢请求？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 2, 'user', '我会先看接口耗时、SQL 执行计划和返回数据量，再判断是查询慢、重复请求多，还是缓存策略缺失。', 7, '定位路径完整，但对监控指标和压测证据说明还不够具体。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 3, 'assistant', '如果同一个报告任务被重复消费，你怎样保证状态一致？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 4, 'user', '我会用会话状态机和幂等键兜底，消费前校验状态，完成后再写报告并广播事件。', 6, '能说出关键机制，但事务边界和失败恢复还需要展开。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 5, 'assistant', '整体回答能覆盖排查主线，建议后续补充具体日志字段、重试策略和数据库约束。', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 6, 'assistant', '那我们继续追问。如果 SSE 推送报告完成事件时浏览器刷新了，你如何保证用户重新进入还能看到最终状态？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 7, 'user', '我会让报告状态以数据库为准，SSE 只做实时通知。页面重进后先查会话状态和报告内容，已完成就直接展示，连接断开不会影响最终一致性。', 8, '能区分通知通道和持久状态，回答比前一轮更落地。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 8, 'assistant', '如果报告任务已经消费成功，但广播失败了，前端一直没有收到事件，你会怎么设计补偿？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 9, 'user', '前端会有轻量轮询兜底，后端也可以在用户重新订阅时检查最近完成的任务并补发一次事件。核心还是状态落库，广播失败不回滚报告。', 7, '补偿思路完整，但可以再说明轮询频率和退避策略。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 10, 'assistant', '最后一个问题，如果让你重构这条链路，你会优先改哪一块？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-23 14:00:00', 11, 'user', '我会优先把任务状态、报告持久化和通知解耦，明确每一步的幂等键和可重试边界，再补充可观测指标。', 8, '收尾回答聚焦，能抓住可维护性和可观测性。'

    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 0, 'system', '你是一名严谨的 Java 后端面试官，请重点考察候选人在 Spring Boot、Redis、消息队列与分布式系统方面的实践能力。', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 1, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 2, 'assistant', '秒杀流量洪峰打过来时，你的 Redis 预扣减如何保证原子性？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 3, 'user', '核心用 Redis Lua 脚本做库存判断和扣减，扣减成功后投递事务消息到 MQ，再异步落库。', 8, '技术链路清晰，能把缓存原子性和异步削峰联系起来。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 4, 'assistant', '使用 Lua 脚本能保证单机原子性。如果库存分片在多个 Redis 节点上，单个 Lua 脚本还能搞定吗？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 5, 'user', '如果是集群架构，会通过 Hash Tag 将同一商品的库存分片路由到同一个 Redis 节点；全局库存聚合则用更粗粒度的分布式锁控制。', 8, '能说明 Redis Cluster 下的局部原子性边界。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 6, 'system', '面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 7, 'assistant', '如果 MQ half message 发送超时，你如何收场？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 8, 'user', '我会用本地事务状态和消息流水做回查，结合 MQ 回查机制判断 commit 或 rollback，并提供补偿任务。', 9, '回答完整，状态回查、补偿和最终一致性表达清楚。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 9, 'assistant', '本地消息表能兜底一致性，但大促期间数据库 TPS 面临极限，如果每笔失败消息都落库，数据库会不会成为瓶颈？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 10, 'user', '生产中不会每次都查表。常规情况依赖 MQ 自动重试，确实需要补偿的消息放到独立补偿库，或者用 Canal 对比流水差异。', 8, '工程取舍清楚，能说明对核心库的保护策略。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 11, 'assistant', '如果 Redisson 持锁节点 Full GC 导致锁过期后继续执行，怎么防御超卖？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 12, 'user', '数据库层加 update stock = stock - 1 where stock > 0 的乐观锁兜底。即使锁失效，最终扣减也会被库存条件挡住。', 8, '能给出最终一致的兜底条件，底层风险意识较好。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 13, 'assistant', '库存拆到多个分片后，跨节点行锁会拖垮吞吐，你会怎么取舍？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 14, 'user', '大促场景下我会避免分布式事务，把库存拆到更细粒度，或者采用流水异步核对和延迟补偿，用极小超卖换吞吐。', 9, '能明确说明一致性和吞吐的工程权衡。'
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 15, 'system', '面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 16, 'assistant', '最后一个问题，如果预算不变，你会优先在哪个环节做架构降级或减负？', NULL, NULL
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 17, 'user', '我会优先在查询链路上做静态化和边缘缓存，把绝大部分读流量拦截在网关前，核心服务只保留扣库存的最小路径。', 9, '收尾回答清晰，能抓住读写分离和核心路径瘦身。'

    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 0, 'system', '你是一名前端工程师岗位的面试官，请重点考察候选人在 Vue、React、浏览器原理、工程化和交互实现方面的理解。', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 1, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 2, 'assistant', '基于 qiankun 做子应用隔离时，你如何处理 CSS 污染和 JS 全局变量冲突？', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 3, 'user', 'JS 侧依赖 Proxy 沙箱，CSS 侧使用样式隔离和命名规范，弹窗类组件单独做容器约束。', 7, '方向正确，但对弹窗、主题变量和卸载清理的细节还可以更充分。'
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 4, 'assistant', '面对多实例子应用的 Pinia 状态隔离，你有没有遇到过子应用切换时状态泄漏？', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 5, 'user', '遇到过。子应用卸载时主动调用核心 store 的 reset，并把状态生命周期绑定到 qiankun 的 mount 和 unmount 钩子。', 7, '能说明状态回收动作，但隔离边界仍可更细。'
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 6, 'system', '面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 7, 'assistant', '虚拟列表快速滚动出现白屏时，你认为瓶颈在哪里？', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 8, 'user', '既有 DOM 替换压力，也有可见区计算压力。我会增加 overscan，用 rAF 节流滚动计算，并缓存动态高度。', 8, '性能排查路径比较完整，能覆盖渲染与计算两侧。'
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 9, 'assistant', '高度不固定的长列表里，ResizeObserver 回调如果触发重排，会造成阻塞。你们怎么避免循环布局更新？', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 10, 'user', '采用预估高度加异步修正，真实高度写入缓存时推迟到 nextTick 或下一帧合并处理，不在同一帧里反复读写布局。', 8, '回答有工程细节，能说明读写分离和批处理。'
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 11, 'assistant', '在线视频面试弱网丢包严重时，单纯音频切片重排不够，你会怎么平滑处理？', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 12, 'user', '如果是 WebRTC，会开启 FEC 或 NACK；如果是 WebSocket 分包，就在 AudioWorklet 缓冲区补短静音包或做轻量时间拉伸，严重时降级文字。', 7, '覆盖了降级思路，但不同传输协议下的边界还可更严谨。'
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 13, 'system', '面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 14, 'assistant', '如果迁移到鸿蒙或 Electron，你觉得现有前端架构阻力最大的是哪里？', NULL, NULL
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 15, 'user', '最大阻力是平台底层 API 差异，比如 WebRTC、iframe、Proxy 沙箱。需要抽出 Adapter 层，让业务只依赖统一接口。', 7, '架构方向正确，平台差异举例清楚。'

    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 0, 'system', '你是一名算法工程师岗位的面试官，请重点考察候选人在数据结构、推荐系统和机器学习基础方面的理解。', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 1, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 2, 'assistant', '双塔召回缺乏细粒度特征交叉时，你们如何缓解？', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 3, 'user', '会在粗排阶段引入轻量交叉网络，对召回结果做二次打分，同时控制线上耗时。', 7, '思路可行，但缺少特征、延迟和召回规模的量化说明。'
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 4, 'assistant', '在多目标排序模型里，你如何处理样本空间偏置和数据稀疏？', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 5, 'user', '会用全样本空间训练，并让 CTR 预估为 CVR 学习提供条件概率信号；稀疏目标通过多任务共享 Embedding 获得更稳定更新。', 7, '能说出 ESSM 和 MTL 思路，但缺少具体损失函数和样本构造。'
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 6, 'system', '面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 7, 'assistant', 'LoRA 微调在多机多卡里通信成为瓶颈时，你怎么处理？', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 8, 'user', 'LoRA 参数较少，通常先用梯度累积减少同步频次；如果全参训练，会考虑 ZeRO 分片、NVLink 机内通信和跨机 AllReduce 拓扑。', 6, '覆盖了术语，但真实多机经验和性能指标说明不足。'
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 9, 'assistant', 'RLHF 中如果奖励模型出现 Reward Hacking，模型学会迎合打分却不解决问题，你怎么应对？', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 10, 'user', '会加入 KL 散度约束，限制 PPO 阶段策略偏离 SFT 模型，并定期抽样高分回答做人工复核和奖励模型回退。', 7, '能说出主要约束机制，但训练数据闭环还可以更具体。'
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 11, 'assistant', '线上点击率断崖下降但服务错误率正常，你第一时间排查什么？', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 12, 'user', '我会先看特征覆盖率、实时日志分布和最近模型发布记录，再比对昨天同期的样本分布。', 6, '排查方向正确，但实验回滚、监控阈值和样本归因还不够落地。'
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 13, 'system', '面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 14, 'assistant', '离线 AUC 上升但线上 A/B 收益为负时，你怎么看？', NULL, NULL
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 15, 'user', '离线指标可能学到了位置偏置或强漏斗特征，线上还要看留存、转化和商业指标，并做显著性检验，不能只看 AUC。', 7, '能意识到离线和线上目标差异，表达比排查题更完整。'
) seed ON seed.`target_position` = s.`target_position` AND seed.`session_created_at` = s.`created_at`
WHERE u.`username` = 'demo';

INSERT INTO `score_history` (`user_id`, `session_id`, `technical_score`, `expression_score`, `logic_score`, `created_at`)
SELECT s.`user_id`, s.`id`, score.`technical_score`, score.`expression_score`, score.`logic_score`, TIMESTAMPADD(MINUTE, 35, s.`created_at`)
FROM `interview_session` s
JOIN `user` u ON s.`user_id` = u.`id`
JOIN (
    SELECT 'Java 后端工程师' AS target_position, '2026-04-23 14:00:00' AS session_created_at, 7 AS technical_score, 6 AS expression_score, 7 AS logic_score
    UNION ALL SELECT 'Java 后端工程师', '2026-04-22 10:00:00', 8, 9, 8
    UNION ALL SELECT '前端工程师', '2026-04-20 16:10:00', 7, 7, 7
    UNION ALL SELECT '算法工程师', '2026-04-18 15:30:00', 6, 7, 6
) score ON score.`target_position` = s.`target_position` AND score.`session_created_at` = s.`created_at`
WHERE u.`username` = 'demo';

-- 4. 非破坏性迁移兜底。
UPDATE `user`
SET `llm_model` = 'deepseek-v4-pro'
WHERE `llm_provider` = 'deepseek'
  AND `llm_model` IN ('deepseek-chat', 'deepseek-reasoner');

UPDATE `user`
SET `llm_provider` = COALESCE(`llm_provider`, 'deepseek'),
    `llm_model` = COALESCE(`llm_model`, 'deepseek-v4-pro')
WHERE `llm_provider` IS NULL OR `llm_model` IS NULL;

UPDATE `interview_session`
SET `llm_model` = 'deepseek-v4-pro'
WHERE `llm_provider` = 'deepseek'
  AND `llm_model` IN ('deepseek-chat', 'deepseek-reasoner');

UPDATE `interview_session`
SET `llm_provider` = COALESCE(`llm_provider`, 'deepseek'),
    `llm_model` = COALESCE(`llm_model`, 'deepseek-v4-pro')
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
