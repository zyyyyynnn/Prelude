# 核心功能验证矩阵（2026-07-15）

本表只登记当前代码与自动化测试可以支持的结论。环境和最终 CI run 以 `quality-gates-2026-07-15.md` 为准。

| ID | 场景 | 关键预期 | 当前证据 | 结论 |
| --- | --- | --- | --- | --- |
| TC-01 | PDF 导入与结构化简历 | 仅 PDF/10 MiB；保存 `ResumeDocument` 与版本；读取可回退旧文本 | resume import/document application 与 controller tests | 自动化通过；扫描件 OCR 未覆盖 |
| TC-02 | 结构化简历编辑 | 前端先加载版本，草稿只在显式保存时提交；版本冲突拒绝覆盖 | `ResumeDocumentEditor.vue`、`resume-improvement.spec.ts`、document update tests | 通过 |
| TC-03 | 开始面试与会话恢复 | 简历/岗位归属校验；会话、消息、阶段可重放 | `StartInterviewTest`、controller/flow tests、session preference tests | 通过 |
| TC-04 | SSE 文字回合 | 用户消息、阶段推进、模型片段与取消使用同一用例和有序消息真源 | `RunInterviewTurn` / `StreamChatTurn` tests、架构测试 | 通过；极端公网丢包率未测 |
| TC-05 | WebSocket 语音回合 | 复用文字核心用例；会话切换、空 STT、TTS 失败/超时与音频顺序受保护 | voice turn/service tests、共享用例架构测试 | 工程容错通过；真实 ASR/TTS 时延未测 |
| TC-06 | 结束面试与异步报告 | `/finish` 返回 job；重复投递可吸收；PENDING/RUNNING 可恢复；终态失败恢复会话 | finish/job/worker/recovery tests | 通过；不代表生产零丢失 |
| TC-07 | 结构化报告与能力沉淀 | 派生分数只使用已落库消息；报告、评分历史、薄弱点和 `report_ready` 一致 | parser/assembler/generation/worker tests、report contract tests | 通过 |
| TC-08 | 证据驱动简历建议 | 最多 3 条；字段白名单、候选人原文证据、用户确认、原文和版本 CAS | editor/improvement/generation/controller tests，前端建议 flow | 通过 |
| TC-09 | 分析看板 | application View 与 API DTO 分离；雷达、趋势、薄弱点按用户读取 | insight service/controller tests、架构测试 | 通过 |
| TC-10 | 三协议 BYOK | OpenAI Responses、Chat Completions、Anthropic Messages 请求/流解析精确；Key 不跨 scope/fallback | Provider contract/config tests、`verify:byok`、provider DTO contract | mock/单元通过；非公网性能证据 |
| TC-11 | 自定义 LLM 出站安全 | 生产 HTTPS/端口白名单；内网/DNS/重绑定/重定向/超长响应拒绝 | egress policy/client tests | 通过；local 配置可显式放宽 |
| TC-12 | 混合检索与退化 | 完整候选融合；持久化向量恢复；模型过期刷新；Embedding 失败走关键词 | retrieval adapter/store/schema tests、容量实验 | 通过；真实语义标注集仍缺失 |
| TC-13 | 鉴权与数据归属 | JWT 保护；简历、会话、建议按当前用户校验 | web/controller/application tests、前端 HTTP 装配 | 通过；多端强制下线未验证 |
| TC-14 | local/dev fixture | `data-dev.sql` 只建测试账号；reset 复用正式 parser/assembler 和结构化文档 | DevFixture tests、local screenshot harness | 通过；prod 不启用 fixture |
| TC-15 | 工程质量门禁 | MySQL schema、Sentrux、后端测试/覆盖率、前端 check/架构/契约/flows/UI/a11y 全部可重复 | `.github/workflows/ci.yml`、`quality-gates-2026-07-15.md` | 本地与 PR #27 双触发 CI 通过 |

## 论证边界

- `verify:byok` 使用 mock API，不是任意公网 endpoint 兼容性或延迟证明。
- `verify:a11y` 只阻断 critical；serious 对比度问题登记在工程风险台账，不等于 WCAG 2 AA 完整合规。
- visual 在 CI 中是 artifact-only；本地 24 个场景通过不等于所有状态的像素差异门禁。
- RabbitMQ、SSE、WebSocket、Redis 与检索测试不支持高并发、零丢失或多实例一致性结论。
