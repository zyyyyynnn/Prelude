INSERT IGNORE INTO `position_template` (`name`, `system_prompt`) VALUES
('Java 后端工程师', '你是一名严谨的 Java 后端面试官，请重点考察候选人在 Spring Boot、JVM、MySQL 与分布式系统方面的基础与实践能力。提问要循序渐进，注重项目经历追问。'),
('前端工程师', '你是一名前端工程师岗位的面试官，请重点考察候选人在 React、TypeScript、浏览器原理、工程化和交互实现方面的理解。提问风格客观直接，注重场景化追问。'),
('算法工程师', '你是一名算法工程师岗位的面试官，请重点考察候选人在数据结构、算法设计和机器学习基础方面的理解与表达。请根据候选人回答逐步加深难度。');

INSERT IGNORE INTO `llm_provider_config` (`provider_key`, `display_name`, `base_url`, `available_models`, `enabled`) VALUES
('deepseek', 'DeepSeek', 'https://api.deepseek.com/chat/completions', '["deepseek-v4-pro","deepseek-v4-flash"]', 1),
('openai-responses', 'OpenAI Responses', '', '[]', 1),
('openai-chat-completions', 'OpenAI Chat Completions', '', '[]', 1),
('anthropic-messages', 'Anthropic Messages', '', '[]', 1);
