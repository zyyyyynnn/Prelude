# 训练闭环与安全边界实现证据（2026-07-15）

本文件登记当前源码入口和可由自动化验证的结论，不替代代码本身。

## 简历改进闭环

| 事实 | 源码 | 验证 |
| --- | --- | --- |
| 报告建议只能使用 summary、工作/项目 bullet、项目 outcome 白名单 | `resume/domain/ResumeDocumentEditor.java` | `ResumeDocumentEditorTest` |
| 建议必须匹配当前字段原文，接受时使用简历版本 CAS | `resume/application/ResumeImprovementService.java` | `ResumeImprovementServiceTest` |
| 建议保存候选人回答原文证据，最多 3 条；无结构化简历时报告仍可完成 | `insight/application/GenerateInterviewReport.java` | `GenerateInterviewReportTest` |
| 前端结构化编辑显式保存，报告页逐项接受/拒绝 | `features/resume/components/ResumeDocumentEditor.vue`、`features/report/components/ResumeImprovementList.vue` | `resume-improvement.spec.ts`、报告契约测试 |

AI 只生成候选建议；字段白名单、证据子串、用户归属、建议状态、原文一致性和文档版本由服务端决定。该链路不提供自动覆盖或无确认批量改写。

## BYOK 出站边界

| 事实 | 源码 | 验证 |
| --- | --- | --- |
| 生产默认 HTTPS 443，拒绝凭证、query、fragment、内部域名和非公网 DNS 结果 | `platform/llm/CustomLlmEgressPolicy.java` | `CustomLlmEgressPolicyTest` |
| DNS 在实际连接时再次校验，HTTP/HTTPS 重定向关闭 | `platform/llm/CustomLlmHttpClient.java` | `CustomLlmHttpClientTest` |
| 普通响应与流式累计响应均限制为 2 MiB，单条流事件限制为 256 KiB，并设置连接/写/读/总调用超时 | 同上 | oversized response/stream 测试 |
| 三种 BYOK 协议失败不使用系统 Provider fallback，Key 不跨 scope 复用 | Provider 与 `UserLlmConfigServiceImpl` | Provider request contract、config service、前端 BYOK/DTO 测试 |

浏览器只收到稳定错误提示，底层上游异常保留在服务端日志。测试覆盖 loopback、私网/metadata、混合 DNS、IPv4 数字形式、IPv4-mapped IPv6、6to4、URL 凭证和 DNS 重绑定。

## Retrieval 与作业恢复

| 事实 | 源码 | 验证 |
| --- | --- | --- |
| chunk、内容哈希、embedding 模型/维度/向量持久化 | `RetrievalChunkStore`、`MybatisRetrievalChunkStore`、`schema.sql` | repository/schema/adapter tests |
| 关键词与向量在完整候选集融合；查询 embedding 故障退化到关键词 | `InMemoryRetrievalAdapter` | keyword union、fallback、stale/malformed snapshot tests |
| 不同 scope 使用 64 个锁条带，快照替换后再发布内存索引 | 同上 | adapter tests |
| PENDING 超时和 RUNNING 租约过期均可补投；错误落库前截断脱敏 | `PendingJobRecoveryPublisher`、`JobExecutionStore` | job recovery/store tests |

容量数据见 `../test-data/retrieval-capacity-2026-07-15.md`。它只支持有限规模结论，不支持生产级 RAG 或高并发表述。

## 模块与数据边界

- insight application 通过 application View 返回分析数据，通过 `JobExecutionPort` 管理作业状态，不导入 API DTO 或 job infrastructure。
- domain 类不含 Spring/MyBatis 注解；核心 application 不导入本模块 infrastructure。
- `schema.sql`、`data.sql`、`data-dev.sql` 分别承担结构、生产安全数据维护和开发账号；不存在日期命名迁移 SQL。
- CI 的 MySQL 8.4 job 对全新建库、模拟旧结构、旧 Provider 值和重复执行进行真实验证。

对应自动化入口：`ArchitectureBoundaryTest`、Sentrux、后端全量测试与 `.github/workflows/ci.yml`。
