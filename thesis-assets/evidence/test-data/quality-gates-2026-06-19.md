# 质量门禁与自动化验证证据快照（2026-06-19）

## 1. 文件定位

本文件是论文第五章“构建与自动化验证记录”的补充证据。

- 对应表格：表 5.3 构建与自动化验证记录表。
- 证据性质：工程质量门禁与自动化验证链路记录。
- 适用边界：仅证明当前项目在本地/CI 质量门禁下具备可重复验证基础，不证明生产环境高并发性能、模型质量稳定性或消息队列零丢失。

## 2. 当前基线

| 项 | 内容 |
| --- | --- |
| 审查基线 | `main` 最新提交 `57eba82` |
| 最近治理范围 | 约最近 20 次提交，重点覆盖 UI token 收敛、dev fixture 收敛、语音链路硬化、消息序号串行化、报告任务幂等、BYOK 验证、Sentrux/JaCoCo/npm audit 门禁 |
| 运行入口口径 | `start-dev` + `start-docker`；旧 Demo Twin / start-real / start-demo 均为历史状态 |
| 论文状态 | 仅补齐 evidence，不修改 `chapters/*.md`；阶段 3 仍需用户和审查官确认后才能启动 |

## 3. CI 与本地质量门禁

| 门禁项 | 当前实现 | 可支撑结论 | 限制 |
| --- | --- | --- | --- |
| whitespace diff check | `.github/workflows/ci.yml` 对 push 使用 `HEAD~1`，对 PR 使用 `base.sha...HEAD` | 可阻断新增 whitespace 错误 | 不等同代码风格全量审查 |
| Sentrux 架构规则 | CI 下载 `sentrux` v0.5.7，SHA256 固定，执行 `sentrux check .` | 架构边界规则已进入 CI 阻塞门禁 | 当前规则数量有限，不等同全量架构证明 |
| 后端测试 | `mvn -q test`；JaCoCo `report` 绑定 `test` phase | 单元测试通过并生成覆盖率报告 | coverage report-only，无覆盖率阈值 |
| JaCoCo artifact | CI 上传 `backend-jacoco-report`，保留 14 天 | 可供审查覆盖率明细 | 不阻塞构建，不作为达标率证明 |
| npm audit | CI 执行 `npm audit --omit=dev`，无 `continue-on-error` | 生产依赖漏洞回潮会阻断 CI | 仅覆盖 npm advisory 范围 |
| 前端构建 | `npm run build` | 类型检查与 Vite 生产构建通过 | 不代表真实浏览器兼容矩阵 |
| BYOK 浏览器验证 | `npm run verify:byok`，首屏 cold-start 等待已硬化，后续 payload/样式断言保留 | OpenAI-compatible endpoint、模型发现、测试连接、保存设置、下拉样式链路可自动化验证 | 使用脚本 mock API，不代表真实公网模型性能 |
| 暗色主题验证 | `npm run verify:dark` | 主题切换和关键画布渲染有自动化 sanity check | 不等同全部 UI 截图回归 |

## 4. 最近小重构带来的证据变化

| 变更域 | 代表提交 | 证据事实 | 论文写作边界 |
| --- | --- | --- | --- |
| 消息序号写入 | `f67abc1`、`e639d94`、`588bf73` | 面试消息写入统一走 `InterviewMessageService`；`InterviewJudgeService.nextSeqNum` 改为 latest max+1，覆盖稀疏序列 | 可写“降低消息乱序/重复风险”，不可写“并发绝对无冲突” |
| 报告任务幂等 | `5f0c786`、`31272bc`、`b39eee1` | `/finish` 对 generating/finished 做幂等保护；`ReportJobWorkerTest` 覆盖生成态、已完成态、ongoing 跳过、UserContext 清理、score/weakness delete-then-insert | 可写“报告生成链路具备幂等保护与回归测试”，不可写“生产级可靠投递” |
| BYOK 与 fallback | `588bf73`、`1cd55d2`、`57eba82` | openai-compatible 失败不静默 fallback；fallback 只走内置 provider 系统 Key；BYOK 前端验证脚本覆盖 provider 切换、模型发现、草稿测试、保存 payload 与下拉样式 | 可写“用户级 OpenAI-compatible 配置可验证”，不可写“所有模型故障零感知切换” |
| 语音链路 | `25ab32f`、`1cd55d2`、`b821bf7` | `VoiceInterviewTurnService` 使用 `ttsTaskExecutor`，TTS 等待 30s timeout，双 flag 避免迟到音频；测试覆盖句子顺序、超时、失败清理 | 可写“语音链路具备工程级容错与顺序保护”，不可写“真实 ASR/TTS 延迟已完成性能基准” |
| 依赖治理 | `31272bc`、`588bf73`、`1cd55d2` | axios/markdown-it 升级；form-data/dompurify overrides 关闭残留 audit；CI 加入 npm audit | 可写“当前生产依赖审计为 0 vulnerabilities”，需说明依赖审计基于 npm advisory |
| 架构/覆盖率门禁 | `b821bf7`、`6098ca0`、`9ab9465` | Sentrux 进入 CI；JaCoCo report-only；文档口径已澄清 constraints 硬失败、coverage 不阻塞 | 不得写成“覆盖率达标”，只能写“生成覆盖率报告供审查” |
| 运行口径 | `51304d7`、`41b27b1`、`c974508` | Demo Twin 退役，收敛为 `start-dev` + `start-docker`，演示数据改为 dev fixture | 旧 Demo 数据只能作为历史对照，不得作为当前默认运行模式 |

## 5. 仍需限制的论文表述

- 不得宣称已完成公网高并发压测。
- 不得宣称 RabbitMQ 已达到生产级可靠投递或消息零丢失。
- 不得宣称 ASR/TTS 在真实公网语音服务下完成低延迟性能基准。
- 不得宣称 BYOK fallback 可对 openai-compatible 自定义接口进行无感系统通道切换。
- 不得把 JaCoCo report-only 写成 coverage threshold gate。
- 不得把 Sentrux 当前有限规则写成完整架构正确性证明。

## 6. 可进入第五章的稳妥写法

> 项目在 2026-06-19 基线下已形成自动化质量门禁：后端单元测试可生成 JaCoCo 覆盖率报告，前端构建、生产依赖审计、BYOK 设置流程验证和暗色主题验证均纳入本地/CI 检查；同时通过 Sentrux 维护基础架构边界约束。上述结果用于说明系统具备可重复的工程验证流程，但不等同于生产环境高并发性能测试或模型服务稳定性证明。
