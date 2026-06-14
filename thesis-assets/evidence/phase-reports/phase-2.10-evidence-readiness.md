# 阶段 2.10 证据 / 图表 / 测试资产准入审查

## 1. 审查边界

* 本阶段不是阶段 3；
* 未修改正文；
* 未冻结引用编号；
* 未生成 DOCX/PDF；
* 只审查 evidence、defense、literature 与治理规范的一致性。

## 2. 当前资产清单

### evidence/
* thesis-assets/evidence/bug-evidence/01-demo-proxy.md
* thesis-assets/evidence/bug-evidence/02-mysql-preflight.md
* thesis-assets/evidence/bug-evidence/package-2026-04-24.md
* thesis-assets/evidence/code-snippets/impl-2026-04-24.md
* thesis-assets/evidence/code-snippets/impl-2026-05-31.md
* thesis-assets/evidence/code-snippets/impl-2026-06-02.md
* thesis-assets/evidence/code-snippets/impl-2026-06-05.md
* thesis-assets/evidence/diagrams/fig-3.1-core-use-case.mmd
* thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png
* thesis-assets/evidence/diagrams/fig-3.2-database-er.mmd
* thesis-assets/evidence/diagrams/fig-3.2-database-er.png
* thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd
* thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png
* thesis-assets/evidence/figure-assets-plan.md
* thesis-assets/evidence/figure-table-register.md
* thesis-assets/evidence/review-notes/fig-3.3-redraw-requirements.md
* thesis-assets/evidence/review-notes/stage-2-evidence-sync.md
* thesis-assets/evidence/test-data/archive/demo-2026-04-25.json
* thesis-assets/evidence/test-data/archive/demo-2026-04-25.md
* thesis-assets/evidence/test-data/demo-2026-06.md
* thesis-assets/evidence/test-data/archive/env-2026-04-24.md
* thesis-assets/evidence/test-data/env-2026-06.md
* thesis-assets/evidence/test-data/functional-cases-2026-06.md
* thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.json
* thesis-assets/evidence/test-data/real-llm-api-2026-05-27-redacted.md

### defense/
* thesis-assets/defense/package-2026-04-25.md
* thesis-assets/defense/script.md
* thesis-assets/defense/slide-map.md

### docs/images/
* docs/images/analytics.png
* docs/images/interview-chat.png
* docs/images/interview-empty.png
* docs/images/interview-report.png
* docs/images/login.png
* docs/images/register.png
* docs/images/resumes.png

### literature/
* thesis-assets/literature/evidence-map.md
* thesis-assets/literature/quality-review.md
* thesis-assets/literature/references.bib

## 3. 图表登记审查

`figure-table-register.md` 存在且包含要求字段。记录了图3.1, 图3.2, 图3.3 等架构图，以及测试表数据。无明显缺失核心列。

## 4. 图表与截图候选

| 文件路径 | 类型 | 可能用途 | 对应章节建议 | 当前状态 | 风险说明 |
| --- | --- | --- | --- | --- | --- |
| `thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png` | 用例图 | 展示核心功能 | 第三章 | 可候选 | 无 |
| `thesis-assets/evidence/diagrams/fig-3.2-database-er.png` | ER图 | 数据库设计 | 第三章 | 可候选 | 无 |
| `thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png` | 架构图 | 系统架构 | 第三章 | 可候选 | 无 |
| `docs/images/analytics.png` | 界面截图 | 展示数据分析功能 | 第四/五章 | 可候选 | 需登记入图表册 |
| `docs/images/interview-chat.png` | 界面截图 | 展示面试聊天页面 | 第四/五章 | 可候选 | 需登记入图表册 |
| `docs/images/interview-empty.png` | 界面截图 | 展示面试空状态 | 第四/五章 | 可候选 | 需登记入图表册 |
| `docs/images/interview-report.png` | 界面截图 | 展示面试报告页面 | 第四/五章 | 可候选 | 需登记入图表册 |
| `docs/images/login.png` | 界面截图 | 展示登录页面 | 第四/五章 | 可候选 | 需登记入图表册 |
| `docs/images/register.png` | 界面截图 | 展示注册页面 | 第四/五章 | 可候选 | 需登记入图表册 |
| `docs/images/resumes.png` | 界面截图 | 展示简历管理页面 | 第四/五章 | 可候选 | 需登记入图表册 |

## 5. 测试与运行证据候选

| 文件路径 | 证据类型 | 可能支撑章节 | 证明内容 | 当前状态 | 风险说明 |
| --- | --- | --- | --- | --- | --- |
| `thesis-assets/evidence/test-data/env-2026-06.md` | 测试环境 | 第五章 | 测试所使用的环境及配置 | 可用 | 无 |
| `thesis-assets/evidence/test-data/functional-cases-2026-06.md` | 测试用例 | 第五章 | 系统各模块的功能测试情况 | 可用 | 无 |
| `thesis-assets/evidence/test-data/demo-2026-06.md` | 测试报告 | 第五章 | Demo 隔离验证等实际性能指标 | 可用 | 部分指标未测，勿过度拔高 |
| `thesis-assets/evidence/bug-evidence/package-2026-04-24.md` | Bug 修复 | 第四/五章 | Vite 代理与 MySQL 错误排查 | 可用 | 避免夸大为系统能力证明 |
| `thesis-assets/evidence/code-snippets/impl-*.md` | 代码实现 | 第三/四章 | 后端 SSE、前台交互的代码事实 | 可用 | 需与当前代码一致 |

## 6. 文献证据一致性

| 检查项 | 结果 | 问题 | 建议 |
| --- | --- | --- | --- |
| 候选编号能在 quality-review 找到 | 一致 | 无 | 保持当前状态 |
| quality-review 候选能在 bib 找到 | 一致 | 无 | 保持当前状态 |
| 官方技术文档仅支撑第三、四章 | 一致 | 无 | 继续作为工程实现参考 |
| VN/RJ 等禁用说明不进入证据 | 一致 | 无 | 维持隔离状态 |

## 7. 答辩材料一致性

| 文件路径 | 类型 | 用途 | 当前状态 | 风险说明 |
| --- | --- | --- | --- | --- |
| `thesis-assets/defense/package-2026-04-25.md` | 答辩物料 | 答辩路线及截图备份 | 可用 | 独立于正文管线，需同步最新口径 |
| `thesis-assets/defense/script.md` | 讲稿 | 答辩讲解 | 可用 | 需与最终定稿的系统能力对齐 |
| `thesis-assets/defense/slide-map.md` | 幻灯片大纲 | PPT和报告结构的映射 | 可用 | 未引用旧文献，相对独立 |

## 8. 阶段 3 准入问题清单

* **P1**: `docs/images/` 下存在部分系统界面截图尚未登记至 `figure-table-register.md`。进入正文排版时如果使用，需预先确认并登记其图题和出处。
* **P2**: 部分性能指标在 Demo 验证阶段尚未进行真实压力测试（如 SSE 长连接并发、限流熔断极端场景）。在最终编写第五章测试报告时应如实阐述测试边界，避免虚假拔高。

## 9. 阶段结论

CONDITIONAL PASS

## 10. 阶段安全声明

* 阶段 3 仍未开始；
* 正文未修改；
* 引用编号未冻结；
* DOCX/PDF 未生成。
