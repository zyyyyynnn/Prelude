# 阶段性功能测试用例登记表

> 历史状态说明：本文最初记录 2026-06 阶段的 Demo Twin / 双轨运行状态。当前版本已收敛为 `start-dev` + `start-docker`，演示数据改为 dev fixture。2026-06-19 已根据最新 `main` 小重构补充质量门禁、BYOK、TTS、fallback、报告任务幂等与消息序号一致性口径。

**原始测试日期**：2026-06-12  
**补充审查日期**：2026-06-19  
**当前测试环境口径**：本机全栈联调 / dev fixture / CI 自动化验证  
**测试版本**：v0.0.1-SNAPSHOT (Backend), v0.0.0 (Frontend)

## 对应关系

本文档对应毕业论文**第五章 表 5.2 功能测试用例表**。

## 核心业务用例清单 (TC-01 ~ TC-12)

| 用例编号 | 用例名称 | 前置条件 | 操作步骤 | 预期结果 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-01 | PDF 简历解析与模板匹配 | 用户处于已登录状态 | 1. 上传简历文件<br>2. 等待解析完成<br>3. 点击确认模板匹配 | 系统成功解析出专业技能与工作经历字段，推荐合适的面试预置模板。 | 历史功能用例通过；当前 dev fixture 主要用于本地验收，不作为复杂多页 PDF 批量解析成功率统计。 | 通过 / 限制性可写 |
| TC-02 | SSE / 文本流式响应 | 面试已建立 | 1. 用户提交回答<br>2. 观察返回的流式文本 | 前端能逐步展示模型返回片段，异常时具备重连或降级提示。 | 历史真实 API 与当前前端流式机制均有代码/测试证据；未完成极端闪断丢包率统计。 | 通过 / 限制性可写 |
| TC-03 | 语音输入与 TTS 下发 | 处于回答状态 | 1. 用户输入语音<br>2. 服务端 STT<br>3. LLM 回复后 TTS 合成并下发音频 | 语音链路失败时能切回文字模式；同一 turn 内多句 TTS 音频顺序保持一致。 | 最新单元测试覆盖会话切换、会话不可用、空 STT、TTS 失败、TTS timeout、UserContext 清理、多句 TTS 顺序与 `sink.audio` 顺序。真实 ASR/TTS 服务端到端延迟仍未实测。 | 部分通过 / 真实性能待实测 |
| TC-04 | 简历上下文动态追问 | 回答完毕一个问题 | 1. 在回答中引入简历上的技术栈盲点<br>2. 等待下一轮问题 | AI 考官能结合前置对话历史与简历细节，发起连续追问。 | 历史功能用例通过；当前代码仍保留上下文构建与阶段推进链路。 | 通过 |
| TC-05 | 面试异常中断与恢复 | 面试进行中 | 1. 刷新页面或断开网络连接<br>2. 重新进入面试间 | 系统能恢复到断开前的对话轮次与状态。 | 具备本地状态恢复与流式中断处理机制；极端网络闪断下的丢包率未形成量化数据。 | 通过 / 限制性可写 |
| TC-06 | 评估报告与雷达图生成 | 面试手动结束 | 1. 点击结束面试<br>2. 等待生成全局报告 | 系统生成总结报告、评分记录、薄弱点与前端图表展示。 | RabbitMQ 报告链路、ReportJobWorker 幂等、score/weakness delete-then-insert、错误恢复与 `report_ready` 广播均有单元测试和 Docker Compose 功能链路证据。 | 通过 / 限制性可写 |
| TC-07 | dev fixture 本地验收数据 | 使用 `start-dev` 或本地测试脚本 | 1. 运行 dev fixture 相关接口或脚本<br>2. 重置本地验收数据 | 本地验收数据可重置，不污染真实运行口径；旧 Demo Twin 不再作为当前默认模式。 | 当前已收敛为 dev fixture；历史 Demo Twin 数据仅保留为 archive 对照。 | 通过 |
| TC-08 | Provider / BYOK 设置流程 | 用户已登录 | 1. 打开设置页 LLM 配置<br>2. 切换 OpenAI-compatible<br>3. 填写 Base URL / API Key<br>4. 检测模型、测试连接、保存设置 | payload 中 baseUrl、apiKey、model 与用户输入一致；模型发现不自动强选首个模型；下拉样式保持统一。 | `npm run verify:byok` 覆盖 provider 切换、模型发现、草稿测试、保存设置、payload 与 combobox 样式；cold-start 等待已加诊断。 | 通过 |
| TC-09 | Provider fallback 与 Key 边界 | 内置 provider 或 openai-compatible 调用失败 | 1. 模拟内置 provider 失败<br>2. 模拟 openai-compatible 失败 | 内置 provider 可 fallback 到已启用内置备用通道并使用系统 Key；openai-compatible 失败必须显式报错，不静默切换系统通道。 | `LlmRouterTest` 覆盖 openai-compatible 不 fallback、fallback 使用系统 Key、不把 openai-compatible 纳入 fallback 列表。 | 通过 |
| TC-10 | 账号鉴权与 JWT | 用户登录后访问受保护路由 | 1. 使用 token 访问工作区<br>2. token 失效时触发拦截器 | 未登录跳转登录页；401 时清理会话并跳转登录。 | 具备基础路由守卫与 HTTP 拦截逻辑。多端登录强制下线、续签压力场景仍未形成实测记录。 | 部分验证 |
| TC-11 | 自动化质量门禁 | CI 或本地预检执行 | 1. 执行后端测试、前端构建、BYOK verify、dark verify、npm audit、Sentrux、diff check | 关键工程门禁可重复执行，失败时阻断或输出诊断。 | 已新增 `quality-gates-2026-06-19.md` 记录；CI 包含 Sentrux、JaCoCo report artifact、npm audit 与前端验证。 | 通过 |
| TC-12 | 消息序号与阶段系统消息一致性 | 面试阶段推进或语音评分写入 | 1. 阶段推进写入 system message<br>2. 用户/助手/评分消息写入 | 系统消息统一走 message service；seqNum 基于 latest max+1，避免稀疏序列下重复。 | `InterviewStageManagerTest`、`InterviewMessageServiceTest`、`InterviewJudgeServiceTest` 覆盖相关契约。 | 通过 |

## 写作限制

- TC-03 不能写成“真实 ASR/TTS 低延迟性能已通过”，只能写成“语音链路具备容错、顺序保护和单元测试覆盖”。
- TC-06 不能写成“RabbitMQ 生产级可靠投递”，只能写成本地 Docker Compose 与单元测试支撑的异步报告任务链路。
- TC-08 不能写成“真实公网模型性能验证”，`verify:byok` 是 mock API 浏览器自动化流程验证。
- TC-09 不能写成“所有 Provider 故障都可无感切换”；openai-compatible 失败必须显式暴露。
- TC-11 不能写成“覆盖率达标”，JaCoCo 当前仅生成 report，不设置阈值。
