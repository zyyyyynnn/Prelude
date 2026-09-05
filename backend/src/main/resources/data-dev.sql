-- Deterministic development acceptance data.
-- Every dev startup restores only the demo account and preserves other local accounts.

INSERT INTO user_account (
  username, password_hash, email, avatar_url, theme_preference, revision, last_operation_id
) VALUES (
  'demo',
  '$argon2id$v=19$m=19456,t=2,p=1$lqr0JdEZ6e08TWKuAGS5OA$Q2d2jojUIcR4eNImjp3ez2SIlQ/7cZvbr4mtJm6duxM',
  'demo@prelude.local',
  NULL,
  'system',
  0,
  NULL
) AS incoming
ON DUPLICATE KEY UPDATE
  password_hash = incoming.password_hash,
  email = incoming.email,
  avatar_url = incoming.avatar_url,
  theme_preference = incoming.theme_preference,
  revision = incoming.revision,
  last_operation_id = incoming.last_operation_id;

SET @demo_account_id = (
  SELECT id FROM user_account WHERE username = 'demo' LIMIT 1
);

CREATE TEMPORARY TABLE IF NOT EXISTS demo_reset_job_id (
  job_id CHAR(36) PRIMARY KEY
);
DELETE FROM demo_reset_job_id;
INSERT INTO demo_reset_job_id (job_id)
SELECT job_id FROM background_job WHERE account_id = @demo_account_id;

DELETE ep FROM EVENT_PUBLICATION ep
INNER JOIN demo_reset_job_id demo_job
  ON LOCATE(demo_job.job_id, ep.SERIALIZED_EVENT) > 0;
DELETE attempt FROM job_attempt attempt
INNER JOIN demo_reset_job_id demo_job ON demo_job.job_id = attempt.job_id;
DELETE FROM background_job WHERE account_id = @demo_account_id;
DROP TEMPORARY TABLE demo_reset_job_id;

DELETE chunk FROM retrieval_chunk chunk
INNER JOIN interview_session session ON session.id = chunk.scope_id
WHERE chunk.scope_type = 'session' AND session.account_id = @demo_account_id;
DELETE chunk FROM retrieval_chunk chunk
INNER JOIN resume candidate_resume ON candidate_resume.id = chunk.scope_id
WHERE chunk.scope_type = 'resume' AND candidate_resume.account_id = @demo_account_id;

DELETE weakness FROM account_weakness weakness
INNER JOIN interview_session session ON session.id = weakness.session_id
WHERE session.account_id = @demo_account_id;
DELETE score FROM score_history score
INNER JOIN interview_session session ON session.id = score.session_id
WHERE session.account_id = @demo_account_id;
DELETE stage FROM interview_stage stage
INNER JOIN interview_session session ON session.id = stage.session_id
WHERE session.account_id = @demo_account_id;
DELETE message FROM interview_message message
INNER JOIN interview_session session ON session.id = message.session_id
WHERE session.account_id = @demo_account_id;

DELETE version FROM artifact_version version
INNER JOIN artifact report_artifact ON report_artifact.id = version.artifact_id
WHERE report_artifact.account_id = @demo_account_id;
DELETE FROM artifact WHERE account_id = @demo_account_id;
DELETE FROM attachment WHERE account_id = @demo_account_id;
DELETE FROM interview_session WHERE account_id = @demo_account_id;
DELETE FROM model_execution_snapshot WHERE account_id = @demo_account_id;
DELETE FROM model_profile WHERE account_id = @demo_account_id;
DELETE FROM provider_credential WHERE account_id = @demo_account_id;
DELETE FROM resume WHERE account_id = @demo_account_id;
DELETE FROM position_template WHERE account_id = @demo_account_id;
DELETE FROM oauth_binding WHERE account_id = @demo_account_id;

INSERT INTO model_profile (
  account_id, provider, model, credential_id, custom_endpoint_url,
  reasoning_level, effective_parameters_json, model_capability_json,
  fallback_capabilities_json, created_at, updated_at
) VALUES (
  @demo_account_id, 'deepseek', 'deepseek-v4-pro', NULL, NULL,
  'AUTO', '{"maxOutputTokens":4096}', NULL, '[]',
  '2026-09-03 08:45:00', '2026-09-03 08:45:00'
);
SET @demo_profile_id = LAST_INSERT_ID();

INSERT INTO resume (
  account_id, file_name, raw_text, parsed_skills, parsed_projects, created_at
) VALUES
(
  @demo_account_id,
  'Java 后端工程师简历.pdf',
  'Java 后端开发，4 年经验。
技能：Java、Spring Boot、MySQL、Redis、RabbitMQ、Docker。
项目：负责订单履约服务的接口与异步任务，日常峰值约 1200 请求/秒；通过索引调整和批量查询将核心接口 P99 从 480ms 降到 210ms。参与库存扣减幂等、消息重试和告警看板建设。',
  '["Java","Spring Boot","MySQL","Redis","RabbitMQ","Docker"]',
  '[{"name":"订单履约服务","description":"负责接口、异步任务与库存幂等，日常峰值约 1200 请求/秒。"},{"name":"查询性能治理","description":"通过索引调整和批量查询将核心接口 P99 从 480ms 降到 210ms。"}]',
  '2026-09-03 09:00:00'
),
(
  @demo_account_id,
  '前端工程师简历.pdf',
  '前端开发，4 年经验。
技能：React、TypeScript、Vite、TanStack Query、Playwright、Web Vitals。
项目：负责运营工作台和组件库维护，统一表单状态与错误提示；为约 2 万行数据的列表接入虚拟滚动，将筛选后的交互等待从约 1.4 秒降到 400ms。参与键盘操作和暗色主题改造。',
  '["React","TypeScript","Vite","TanStack Query","Playwright","Web Vitals"]',
  '[{"name":"运营工作台","description":"统一表单状态、错误提示和数据请求边界。"},{"name":"长列表性能优化","description":"为约 2 万行数据的列表接入虚拟滚动，将交互等待从约 1.4 秒降到 400ms。"}]',
  '2026-09-03 09:05:00'
),
(
  @demo_account_id,
  '算法工程师简历.pdf',
  '推荐算法开发，3 年经验。
技能：Python、PyTorch、LightGBM、Spark、Faiss、A/B 测试。
项目：维护内容推荐的召回与排序链路，日处理约 5000 万条行为日志；将候选集召回率从 82% 提升到 86%，并参与特征监控、离线评估和小流量实验。',
  '["Python","PyTorch","LightGBM","Spark","Faiss","A/B 测试"]',
  '[{"name":"内容推荐链路","description":"维护召回与排序，日处理约 5000 万条行为日志。"},{"name":"召回质量优化","description":"将候选集召回率从 82% 提升到 86%，并参与离线评估和小流量实验。"}]',
  '2026-09-03 09:10:00'
);

SET @resume_java_id = (SELECT id FROM resume WHERE account_id = @demo_account_id AND file_name = 'Java 后端工程师简历.pdf');
SET @resume_frontend_id = (SELECT id FROM resume WHERE account_id = @demo_account_id AND file_name = '前端工程师简历.pdf');
SET @resume_algorithm_id = (SELECT id FROM resume WHERE account_id = @demo_account_id AND file_name = '算法工程师简历.pdf');
SET @position_java_id = (SELECT id FROM position_template WHERE account_id IS NULL AND name = 'Java 后端工程师' LIMIT 1);
SET @position_frontend_id = (SELECT id FROM position_template WHERE account_id IS NULL AND name = '前端工程师' LIMIT 1);
SET @position_algorithm_id = (SELECT id FROM position_template WHERE account_id IS NULL AND name = '算法工程师' LIMIT 1);
SET @model_capability_json = '{"provider":"deepseek","model":"deepseek-v4-pro","reasoning":true,"structuredOutput":true,"toolCalling":true,"streaming":true,"vision":false,"multilingual":true,"longContext":true,"embedding":false,"nativeRealtimeVoice":false,"supportedReasoningLevels":["AUTO","LOW","HIGH","MAX"]}';

INSERT INTO model_execution_snapshot (
  account_id, profile_id, provider, model, reasoning_level,
  effective_parameters_json, capability_version, model_capability_json,
  fallback_capabilities_json, credential_id, custom_endpoint_url, created_at
) VALUES
  (@demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO', '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-09-03 10:00:00'),
  (@demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO', '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-09-03 14:00:00'),
  (@demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO', '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-09-04 09:00:00'),
  (@demo_account_id, @demo_profile_id, 'deepseek', 'deepseek-v4-pro', 'AUTO', '{"maxOutputTokens":4096}', '2026-09-03', @model_capability_json, '[]', NULL, NULL, '2026-09-05 09:00:00');

SET @snapshot_java_1 = (SELECT id FROM model_execution_snapshot WHERE account_id = @demo_account_id AND created_at = '2026-09-03 10:00:00');
SET @snapshot_frontend_1 = (SELECT id FROM model_execution_snapshot WHERE account_id = @demo_account_id AND created_at = '2026-09-03 14:00:00');
SET @snapshot_algorithm_1 = (SELECT id FROM model_execution_snapshot WHERE account_id = @demo_account_id AND created_at = '2026-09-04 09:00:00');
SET @snapshot_algorithm_ongoing = (SELECT id FROM model_execution_snapshot WHERE account_id = @demo_account_id AND created_at = '2026-09-05 09:00:00');

-- Java 后端：订单幂等与故障恢复。
SET @q1 = '请先介绍你在订单履约服务中负责的范围，以及你如何判断改造是否有效。';
SET @a1 = '我负责下单后的履约接口和异步任务。改造前先记录接口 P95、P99 和失败率，完成索引与批量查询调整后，核心接口 P99 从 480ms 降到 210ms，错误率保持在千分之一以内。';
SET @h1 = '结果和个人职责都清楚，可以再说明数据采集周期。';
SET @q2 = '同一个支付回调重复到达时，你如何保证订单状态只推进一次？';
SET @a2 = '我会用支付单号作为幂等键，先插入带唯一索引的处理记录，再在同一事务里按当前状态更新订单。重复请求命中唯一键后读取已有结果，不再重复发履约消息。';
SET @h2 = '幂等键和事务边界明确，还可以补充首次处理失败后的重试状态。';
SET @q3 = '如果数据库事务提交成功，但消息发布失败，你会怎样恢复？';
SET @a3 = '我会在本地事务中同时写出站消息，由后台任务扫描未发布记录并重试；消费者仍按业务键去重。以前的项目只看重试成功率，没有明确统计最长恢复时间，这是我会补上的指标。';
SET @h3 = '恢复路径合理，但最长恢复时间和积压告警阈值仍需量化。';
SET @q4 = '如果下周接手这条链路，你会优先补哪一项可靠性验证？';
SET @a4 = '我会先做消息中间件短时不可用的演练，记录积压量、恢复耗时和重复消费比例，再据此设置告警阈值，并验证人工补偿入口确实可用。';
SET @h4 = '收尾具体，后续应把恢复目标写进演练验收标准。';

INSERT INTO interview_session (
  account_id, resume_id, position_id, target_position, model_execution_snapshot_id,
  status, summary, summary_report, jd_text, created_at
) VALUES (
  @demo_account_id, @resume_java_id, @position_java_id, 'Java 后端工程师',
  @snapshot_java_1, 'finished', '围绕订单幂等、消息恢复与可观测性完成四个阶段。',
  JSON_OBJECT(
    'summary', JSON_OBJECT('fitAssessment', '具备中级 Java 后端岗位所需的事务、幂等和异步处理经验。', 'actionRecommendation', '可进入下一轮，并重点验证故障演练与容量分析。', 'overallRisk', '恢复流程完整，但恢复时间目标和积压阈值缺少历史量化。'),
    'scores', JSON_OBJECT('technical', 6, 'expression', 7, 'logic', 6, 'overall', 6.3),
    'stagePerformances', JSON_ARRAY(
      JSON_OBJECT('stageName', 'warmup', 'score', 7, 'summary', '职责和性能结果表达清楚。', 'positiveSignals', JSON_ARRAY('说明了负责范围和 P99 改善'), 'negativeSignals', JSON_ARRAY('数据采集周期未说明'), 'improvementSuggestions', JSON_ARRAY('补充指标观察窗口')),
      JSON_OBJECT('stageName', 'technical', 'score', 7, 'summary', '能够给出可落地的幂等方案。', 'positiveSignals', JSON_ARRAY('使用唯一键和事务状态推进'), 'negativeSignals', JSON_ARRAY('首次失败状态处理略简略'), 'improvementSuggestions', JSON_ARRAY('补充失败重试状态机')),
      JSON_OBJECT('stageName', 'deep_dive', 'score', 6, 'summary', '理解事务消息恢复路径。', 'positiveSignals', JSON_ARRAY('提出出站消息和消费去重'), 'negativeSignals', JSON_ARRAY('未量化最长恢复时间'), 'improvementSuggestions', JSON_ARRAY('定义恢复时间和积压阈值')),
      JSON_OBJECT('stageName', 'closing', 'score', 6, 'summary', '能将改进方向落到演练。', 'positiveSignals', JSON_ARRAY('提出中间件故障演练'), 'negativeSignals', JSON_ARRAY('尚未给出验收目标值'), 'improvementSuggestions', JSON_ARRAY('为演练设定可验证目标'))
    ),
    'questionReviews', JSON_ARRAY(
      JSON_OBJECT('stageName', 'warmup', 'question', @q1, 'answerSummary', @a1, 'score', 7, 'scoringReason', @h1, 'improvementSuggestion', '补充性能数据的采集周期和样本量。'),
      JSON_OBJECT('stageName', 'technical', 'question', @q2, 'answerSummary', @a2, 'score', 7, 'scoringReason', @h2, 'improvementSuggestion', '说明首次处理失败时幂等记录如何回到可重试状态。'),
      JSON_OBJECT('stageName', 'deep_dive', 'question', @q3, 'answerSummary', @a3, 'score', 6, 'scoringReason', @h3, 'improvementSuggestion', '给出最长恢复时间、积压阈值和人工介入条件。'),
      JSON_OBJECT('stageName', 'closing', 'question', @q4, 'answerSummary', @a4, 'score', 6, 'scoringReason', @h4, 'improvementSuggestion', '把恢复目标写成故障演练的通过条件。')
    ),
    'strengths', JSON_ARRAY('能说明事务和幂等边界', '性能数据表达具体'),
    'weaknesses', JSON_ARRAY('故障恢复量化：尚未定义最长恢复时间和积压告警阈值。'),
    'trainingPlan', JSON_OBJECT('threeDay', JSON_ARRAY('整理一次消息发布失败的恢复时序图'), 'sevenDay', JSON_ARRAY('完成带恢复时间指标的故障演练'), 'nextInterviewFocus', JSON_ARRAY('异步链路恢复目标与告警设计')),
    'finalAdvice', '保持当前工程化表达，并用恢复时间、积压量和人工介入条件补全可靠性论证。'
  ),
  NULL, '2026-09-03 10:00:00'
);
SET @session_java_1 = LAST_INSERT_ID();
INSERT INTO interview_stage (session_id, stage_name, started_at, ended_at) VALUES
  (@session_java_1, 'warmup', '2026-09-03 10:00:00', '2026-09-03 10:08:00'),
  (@session_java_1, 'technical', '2026-09-03 10:08:00', '2026-09-03 10:16:00'),
  (@session_java_1, 'deep_dive', '2026-09-03 10:16:00', '2026-09-03 10:25:00'),
  (@session_java_1, 'closing', '2026-09-03 10:25:00', '2026-09-03 10:31:00');
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at)
SELECT @session_java_1, 'system', system_prompt, 0, NULL, NULL, '2026-09-03 10:00:00' FROM position_template WHERE id = @position_java_id;
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at) VALUES
  (@session_java_1, 'assistant', @q1, 1, NULL, NULL, '2026-09-03 10:01:00'),
  (@session_java_1, 'user', @a1, 2, 7, @h1, '2026-09-03 10:04:00'),
  (@session_java_1, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈和工程实践追问。', 3, NULL, NULL, '2026-09-03 10:08:00'),
  (@session_java_1, 'assistant', @q2, 4, NULL, NULL, '2026-09-03 10:09:00'),
  (@session_java_1, 'user', @a2, 5, 7, @h2, '2026-09-03 10:13:00'),
  (@session_java_1, 'system', '面试已进入深挖阶段，请针对回答中的边界与失败路径继续追问。', 6, NULL, NULL, '2026-09-03 10:16:00'),
  (@session_java_1, 'assistant', @q3, 7, NULL, NULL, '2026-09-03 10:17:00'),
  (@session_java_1, 'user', @a3, 8, 6, @h3, '2026-09-03 10:22:00'),
  (@session_java_1, 'system', '面试已进入收尾阶段，请用总结性问题结束本场面试。', 9, NULL, NULL, '2026-09-03 10:25:00'),
  (@session_java_1, 'assistant', @q4, 10, NULL, NULL, '2026-09-03 10:26:00'),
  (@session_java_1, 'user', @a4, 11, 6, @h4, '2026-09-03 10:30:00');
INSERT INTO score_history (account_id, session_id, technical_score, expression_score, logic_score, created_at)
VALUES (@demo_account_id, @session_java_1, 6, 7, 6, '2026-09-03 10:32:00');
INSERT INTO account_weakness (account_id, session_id, category, description, created_at)
VALUES (@demo_account_id, @session_java_1, '故障恢复量化', '尚未定义最长恢复时间和积压告警阈值。', '2026-09-03 10:33:00');

-- 前端：复杂表单、性能与可访问性。
SET @q1 = '请介绍你在运营工作台中承担的主要工作，以及这次改造解决了什么问题。';
SET @a1 = '我负责表单框架和数据请求层。原来各页面自己处理保存、错误和加载状态，我把这些状态收敛到统一组件和请求 hooks，减少重复代码，也让失败提示和重试入口保持一致。';
SET @h1 = '职责和问题背景完整，可以补充上线后的缺陷变化。';
SET @q2 = '一个页面有多段可独立保存的表单时，你会怎样避免状态互相污染？';
SET @a2 = '每段表单拥有独立 schema、默认值和提交 mutation，服务端数据只通过 query cache 进入。保存成功后按资源键失效缓存，不把临时输入直接写进全局 store。';
SET @h2 = '状态所有权划分清晰，适合继续讨论并发保存。';
SET @q3 = '长列表从 1.4 秒降到 400ms，你如何确认瓶颈确实在渲染而不是接口？';
SET @a3 = '我先拆分接口耗时、脚本执行和渲染时间，接口稳定在 180ms 左右，主要时间花在大量节点挂载。接入虚拟滚动后又用 Performance 面板和用户计时重复采样，渲染段明显下降。';
SET @h3 = '有分段测量和复测，仍可说明低端设备样本。';
SET @q4 = '这套工作台的键盘和读屏体验是怎样验收的？';
SET @a4 = '我为核心表单补了 Tab 顺序、焦点回收和错误关联，人工走过键盘流程；当时只覆盖主要页面，没有建立自动化 axe 扫描和读屏回归清单，这部分并不完整。';
SET @h4 = '能准确指出覆盖缺口，需要把可访问性验收纳入持续检查。';

INSERT INTO interview_session (
  account_id, resume_id, position_id, target_position, model_execution_snapshot_id,
  status, summary, summary_report, jd_text, created_at
) VALUES (
  @demo_account_id, @resume_frontend_id, @position_frontend_id, '前端工程师',
  @snapshot_frontend_1, 'finished', '围绕状态所有权、长列表性能和可访问性完成四个阶段。',
  JSON_OBJECT(
    'summary', JSON_OBJECT('fitAssessment', '具备中级前端岗位所需的 React 状态管理、性能分析和组件治理能力。', 'actionRecommendation', '可进入下一轮，并通过实际任务验证可访问性与边界处理。', 'overallRisk', '主要页面有键盘适配，但自动化可访问性回归仍不完整。'),
    'scores', JSON_OBJECT('technical', 8, 'expression', 6, 'logic', 7, 'overall', 7.0),
    'stagePerformances', JSON_ARRAY(
      JSON_OBJECT('stageName', 'warmup', 'score', 7, 'summary', '能说明职责与状态收敛目标。', 'positiveSignals', JSON_ARRAY('明确区分保存、错误和加载状态'), 'negativeSignals', JSON_ARRAY('缺少缺陷率变化'), 'improvementSuggestions', JSON_ARRAY('补充上线后的质量指标')),
      JSON_OBJECT('stageName', 'technical', 'score', 7, 'summary', '状态所有权和缓存边界合理。', 'positiveSignals', JSON_ARRAY('避免将临时输入写入全局状态'), 'negativeSignals', JSON_ARRAY('并发保存冲突未展开'), 'improvementSuggestions', JSON_ARRAY('补充 revision 或冲突提示策略')),
      JSON_OBJECT('stageName', 'deep_dive', 'score', 8, 'summary', '性能定位过程有测量依据。', 'positiveSignals', JSON_ARRAY('拆分网络、脚本和渲染耗时'), 'negativeSignals', JSON_ARRAY('设备覆盖说明不足'), 'improvementSuggestions', JSON_ARRAY('增加低端设备与长任务样本')),
      JSON_OBJECT('stageName', 'closing', 'score', 5, 'summary', '能识别可访问性验证缺口。', 'positiveSignals', JSON_ARRAY('覆盖焦点与错误关联'), 'negativeSignals', JSON_ARRAY('没有自动化扫描和读屏清单'), 'improvementSuggestions', JSON_ARRAY('建立可访问性持续验收'))
    ),
    'questionReviews', JSON_ARRAY(
      JSON_OBJECT('stageName', 'warmup', 'question', @q1, 'answerSummary', @a1, 'score', 7, 'scoringReason', @h1, 'improvementSuggestion', '补充上线前后的重复缺陷或维护成本变化。'),
      JSON_OBJECT('stageName', 'technical', 'question', @q2, 'answerSummary', @a2, 'score', 7, 'scoringReason', @h2, 'improvementSuggestion', '说明两段表单同时保存时的冲突处理。'),
      JSON_OBJECT('stageName', 'deep_dive', 'question', @q3, 'answerSummary', @a3, 'score', 8, 'scoringReason', @h3, 'improvementSuggestion', '加入低端设备和长任务指标的复测结果。'),
      JSON_OBJECT('stageName', 'closing', 'question', @q4, 'answerSummary', @a4, 'score', 5, 'scoringReason', @h4, 'improvementSuggestion', '建立 axe 自动化扫描和读屏回归清单。')
    ),
    'strengths', JSON_ARRAY('状态所有权清晰', '性能优化有前后测量'),
    'weaknesses', JSON_ARRAY('可访问性验证：尚未建立自动化扫描和读屏回归清单。'),
    'trainingPlan', JSON_OBJECT('threeDay', JSON_ARRAY('整理核心流程的键盘与焦点清单'), 'sevenDay', JSON_ARRAY('为工作台补充 axe 和读屏回归'), 'nextInterviewFocus', JSON_ARRAY('复杂表单冲突与可访问性验收')),
    'finalAdvice', '继续保持基于状态所有权和测量结果的表达，并补齐可访问性持续验证。'
  ),
  '负责 React 工作台开发，重视 TypeScript、性能测量、组件复用和可访问性。', '2026-09-03 14:00:00'
);
SET @session_frontend_1 = LAST_INSERT_ID();
INSERT INTO interview_stage (session_id, stage_name, started_at, ended_at) VALUES
  (@session_frontend_1, 'warmup', '2026-09-03 14:00:00', '2026-09-03 14:08:00'),
  (@session_frontend_1, 'technical', '2026-09-03 14:08:00', '2026-09-03 14:17:00'),
  (@session_frontend_1, 'deep_dive', '2026-09-03 14:17:00', '2026-09-03 14:26:00'),
  (@session_frontend_1, 'closing', '2026-09-03 14:26:00', '2026-09-03 14:33:00');
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at)
SELECT @session_frontend_1, 'system', system_prompt, 0, NULL, NULL, '2026-09-03 14:00:00' FROM position_template WHERE id = @position_frontend_id;
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at) VALUES
  (@session_frontend_1, 'assistant', @q1, 1, NULL, NULL, '2026-09-03 14:01:00'),
  (@session_frontend_1, 'user', @a1, 2, 7, @h1, '2026-09-03 14:05:00'),
  (@session_frontend_1, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈和工程实践追问。', 3, NULL, NULL, '2026-09-03 14:08:00'),
  (@session_frontend_1, 'assistant', @q2, 4, NULL, NULL, '2026-09-03 14:09:00'),
  (@session_frontend_1, 'user', @a2, 5, 7, @h2, '2026-09-03 14:14:00'),
  (@session_frontend_1, 'system', '面试已进入深挖阶段，请针对回答中的边界与失败路径继续追问。', 6, NULL, NULL, '2026-09-03 14:17:00'),
  (@session_frontend_1, 'assistant', @q3, 7, NULL, NULL, '2026-09-03 14:18:00'),
  (@session_frontend_1, 'user', @a3, 8, 8, @h3, '2026-09-03 14:23:00'),
  (@session_frontend_1, 'system', '面试已进入收尾阶段，请用总结性问题结束本场面试。', 9, NULL, NULL, '2026-09-03 14:26:00'),
  (@session_frontend_1, 'assistant', @q4, 10, NULL, NULL, '2026-09-03 14:27:00'),
  (@session_frontend_1, 'user', @a4, 11, 5, @h4, '2026-09-03 14:32:00');
INSERT INTO score_history (account_id, session_id, technical_score, expression_score, logic_score, created_at)
VALUES (@demo_account_id, @session_frontend_1, 8, 6, 7, '2026-09-03 14:34:00');
INSERT INTO account_weakness (account_id, session_id, category, description, created_at)
VALUES (@demo_account_id, @session_frontend_1, '可访问性验证', '尚未建立自动化扫描和读屏回归清单。', '2026-09-03 14:35:00');

-- 算法：召回评估与线上归因。
SET @q1 = '请介绍你维护的推荐链路规模，以及你在召回优化中负责的部分。';
SET @a1 = '链路每天处理约 5000 万条行为日志，我负责一条向量召回和部分离线评估。主要改动是清理训练样本时间窗并调整负样本抽样，候选集召回率从 82% 提升到 86%。';
SET @h1 = '规模、职责和结果清楚，可以说明指标口径。';
SET @q2 = '离线计算召回率时，正样本集合和时间窗口是怎样定义的？';
SET @a2 = '我们用用户后续七天真实点击的内容作为正样本，训练和评估按时间切分，避免未来信息泄漏。对新内容单独分桶，避免总体指标掩盖冷启动效果。';
SET @h2 = '时间切分和分桶合理，建议补充过滤规则。';
SET @q3 = '离线召回率提升后，线上点击率没有变化，你会从哪里开始排查？';
SET @a3 = '我会先核对召回候选是否进入排序，再比较新旧候选的覆盖率和排序分位。如果流量实验正常，还要按用户活跃度和内容新鲜度分层。之前我们只对比最近三天平均值，归因证据不够细。';
SET @h3 = '排查方向正确，但实验分层和置信区间不足。';
SET @q4 = '下一次实验你会怎样改进，避免再次得到难以解释的结果？';
SET @a4 = '上线前先固定主指标、护栏指标和分层维度，记录实验版本与样本量；运行满一个业务周期后再判断，并保留候选进入排序和最终曝光的漏斗日志。';
SET @h4 = '方案可执行，还应提前约定显著性和停止规则。';

INSERT INTO interview_session (
  account_id, resume_id, position_id, target_position, model_execution_snapshot_id,
  status, summary, summary_report, jd_text, created_at
) VALUES (
  @demo_account_id, @resume_algorithm_id, @position_algorithm_id, '算法工程师',
  @snapshot_algorithm_1, 'finished', '围绕召回率口径、时间切分和线上归因完成四个阶段。',
  JSON_OBJECT(
    'summary', JSON_OBJECT('fitAssessment', '具备中级推荐算法岗位所需的数据处理、召回评估和实验基础。', 'actionRecommendation', '可进入下一轮，并重点验证实验设计与线上归因。', 'overallRisk', '排查路径完整，但已有实验的分层和统计证据偏弱。'),
    'scores', JSON_OBJECT('technical', 6, 'expression', 5, 'logic', 6, 'overall', 5.7),
    'stagePerformances', JSON_ARRAY(
      JSON_OBJECT('stageName', 'warmup', 'score', 7, 'summary', '能说明数据规模和个人职责。', 'positiveSignals', JSON_ARRAY('给出召回率前后变化'), 'negativeSignals', JSON_ARRAY('指标口径未在开场说明'), 'improvementSuggestions', JSON_ARRAY('同步说明评估口径')),
      JSON_OBJECT('stageName', 'technical', 'score', 7, 'summary', '理解时间切分和冷启动分桶。', 'positiveSignals', JSON_ARRAY('主动避免未来信息泄漏'), 'negativeSignals', JSON_ARRAY('样本过滤规则未展开'), 'improvementSuggestions', JSON_ARRAY('补充异常样本过滤')),
      JSON_OBJECT('stageName', 'deep_dive', 'score', 5, 'summary', '有线上排查路径但归因证据不足。', 'positiveSignals', JSON_ARRAY('关注召回到曝光漏斗'), 'negativeSignals', JSON_ARRAY('只比较短期平均值'), 'improvementSuggestions', JSON_ARRAY('增加分层与置信区间')),
      JSON_OBJECT('stageName', 'closing', 'score', 6, 'summary', '能提出更完整的实验记录方案。', 'positiveSignals', JSON_ARRAY('包含护栏指标和漏斗日志'), 'negativeSignals', JSON_ARRAY('停止规则未明确'), 'improvementSuggestions', JSON_ARRAY('预先约定显著性与停止规则'))
    ),
    'questionReviews', JSON_ARRAY(
      JSON_OBJECT('stageName', 'warmup', 'question', @q1, 'answerSummary', @a1, 'score', 7, 'scoringReason', @h1, 'improvementSuggestion', '在结果后补充召回率的候选集合定义。'),
      JSON_OBJECT('stageName', 'technical', 'question', @q2, 'answerSummary', @a2, 'score', 7, 'scoringReason', @h2, 'improvementSuggestion', '补充机器人流量和异常行为的过滤规则。'),
      JSON_OBJECT('stageName', 'deep_dive', 'question', @q3, 'answerSummary', @a3, 'score', 5, 'scoringReason', @h3, 'improvementSuggestion', '按用户与内容分层，并报告置信区间。'),
      JSON_OBJECT('stageName', 'closing', 'question', @q4, 'answerSummary', @a4, 'score', 6, 'scoringReason', @h4, 'improvementSuggestion', '在实验前写清显著性阈值和停止规则。')
    ),
    'strengths', JSON_ARRAY('理解时间切分与数据泄漏', '能沿召回到曝光漏斗排查'),
    'weaknesses', JSON_ARRAY('线上实验归因：已有实验缺少分层结果和置信区间。'),
    'trainingPlan', JSON_OBJECT('threeDay', JSON_ARRAY('复盘一次召回实验的指标口径'), 'sevenDay', JSON_ARRAY('完成带分层和置信区间的实验分析'), 'nextInterviewFocus', JSON_ARRAY('实验停止规则与线上归因')),
    'finalAdvice', '加强统计证据和实验预注册，避免用短期平均值代替线上归因。'
  ),
  '负责推荐召回与离线评估，熟悉 Python、Spark、Faiss 和 A/B 实验。', '2026-09-04 09:00:00'
);
SET @session_algorithm_1 = LAST_INSERT_ID();
INSERT INTO interview_stage (session_id, stage_name, started_at, ended_at) VALUES
  (@session_algorithm_1, 'warmup', '2026-09-04 09:00:00', '2026-09-04 09:08:00'),
  (@session_algorithm_1, 'technical', '2026-09-04 09:08:00', '2026-09-04 09:17:00'),
  (@session_algorithm_1, 'deep_dive', '2026-09-04 09:17:00', '2026-09-04 09:26:00'),
  (@session_algorithm_1, 'closing', '2026-09-04 09:26:00', '2026-09-04 09:33:00');
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at)
SELECT @session_algorithm_1, 'system', system_prompt, 0, NULL, NULL, '2026-09-04 09:00:00' FROM position_template WHERE id = @position_algorithm_id;
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at) VALUES
  (@session_algorithm_1, 'assistant', @q1, 1, NULL, NULL, '2026-09-04 09:01:00'),
  (@session_algorithm_1, 'user', @a1, 2, 7, @h1, '2026-09-04 09:05:00'),
  (@session_algorithm_1, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈和工程实践追问。', 3, NULL, NULL, '2026-09-04 09:08:00'),
  (@session_algorithm_1, 'assistant', @q2, 4, NULL, NULL, '2026-09-04 09:09:00'),
  (@session_algorithm_1, 'user', @a2, 5, 7, @h2, '2026-09-04 09:14:00'),
  (@session_algorithm_1, 'system', '面试已进入深挖阶段，请针对回答中的边界与失败路径继续追问。', 6, NULL, NULL, '2026-09-04 09:17:00'),
  (@session_algorithm_1, 'assistant', @q3, 7, NULL, NULL, '2026-09-04 09:18:00'),
  (@session_algorithm_1, 'user', @a3, 8, 5, @h3, '2026-09-04 09:23:00'),
  (@session_algorithm_1, 'system', '面试已进入收尾阶段，请用总结性问题结束本场面试。', 9, NULL, NULL, '2026-09-04 09:26:00'),
  (@session_algorithm_1, 'assistant', @q4, 10, NULL, NULL, '2026-09-04 09:27:00'),
  (@session_algorithm_1, 'user', @a4, 11, 6, @h4, '2026-09-04 09:32:00');
INSERT INTO score_history (account_id, session_id, technical_score, expression_score, logic_score, created_at)
VALUES (@demo_account_id, @session_algorithm_1, 6, 5, 6, '2026-09-04 09:34:00');
INSERT INTO account_weakness (account_id, session_id, category, description, created_at)
VALUES (@demo_account_id, @session_algorithm_1, '线上实验归因', '已有实验缺少分层结果和置信区间。', '2026-09-04 09:35:00');

-- 当前进行中的算法面试。
INSERT INTO interview_session (
  account_id, resume_id, position_id, target_position, model_execution_snapshot_id,
  status, summary, summary_report, jd_text, created_at
) VALUES (
  @demo_account_id, @resume_algorithm_id, @position_algorithm_id, '算法工程师',
  @snapshot_algorithm_ongoing, 'ongoing', NULL, NULL,
  '参与推荐模型迭代、特征质量监控和线上实验分析。', '2026-09-05 09:00:00'
);
SET @session_ongoing_id = LAST_INSERT_ID();
INSERT INTO interview_stage (session_id, stage_name, started_at, ended_at) VALUES
  (@session_ongoing_id, 'warmup', '2026-09-05 09:00:00', '2026-09-05 09:08:00'),
  (@session_ongoing_id, 'technical', '2026-09-05 09:08:00', NULL);
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at)
SELECT @session_ongoing_id, 'system', system_prompt, 0, NULL, NULL, '2026-09-05 09:00:00' FROM position_template WHERE id = @position_algorithm_id;
INSERT INTO interview_message (session_id, role, content, seq_num, score, hint, created_at) VALUES
  (@session_ongoing_id, 'assistant', '请介绍你最近一次推荐效果优化中负责的范围。', 1, NULL, NULL, '2026-09-05 09:01:00'),
  (@session_ongoing_id, 'user', '我负责向量召回的数据准备和离线评估，通过调整样本时间窗和负样本抽样，将候选集召回率从 82% 提升到 86%。', 2, 7, '职责和结果清楚，可以继续说明评估口径。', '2026-09-05 09:05:00'),
  (@session_ongoing_id, 'system', '面试已进入技术问答阶段，请围绕岗位核心技术栈和工程实践追问。', 3, NULL, NULL, '2026-09-05 09:08:00'),
  (@session_ongoing_id, 'assistant', '为了避免未来信息泄漏，你会怎样划分训练集和验证集？', 4, NULL, NULL, '2026-09-05 09:09:00');
