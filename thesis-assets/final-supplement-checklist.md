# 终稿前补料状态表

## 说明

本表用于锁定论文终稿前资料状态。当前采用“已完成 / 仍需人工终审 / 预留待测 / 可选补充”口径，避免与证据锁版记录和第五章测试数据互相矛盾。

## A. 已完成项

| 类别 | 内容 | 证据位置 | 状态 |
| --- | --- | --- | --- |
| 第三章图表 | 系统整体架构图、系统核心用例图、数据库 E-R 图 | `thesis-assets/evidence/diagrams/`、`thesis-assets/evidence/figure-table-register.md` | 已完成 |
| 第五章测试环境 | Windows、PowerShell、JDK、Maven、Node.js、npm、MySQL、浏览器和网络环境 | `thesis-assets/evidence/test-data/test-evidence-2026-04-24.md` | 已完成 |
| Demo 业务测试 | `TC-01` 到 `TC-09` 功能测试结果 | `thesis-assets/evidence/test-data/demo-business-test-2026-04-25.md` | 已完成 |
| Demo 性能采集 | SSE TTFB、SSE 总耗时、PDF 上传/解析接口耗时 | `thesis-assets/evidence/test-data/demo-business-test-2026-04-25.md` | 已完成；仅代表 Demo Twin 本机回环环境 |
| 构建验证 | 后端 `mvn -q test`、前端 `npm run build` | `thesis-assets/evidence/test-data/test-evidence-2026-04-24.md` | 已完成 |
| 实现证据 | SSE 推送、PDF 解析、上下文截断核心代码片段 | `thesis-assets/evidence/code-snippets/implementation-evidence-2026-04-24.md` | 已完成 |
| 实现证据 | LLM 柔性降级、PDF 安全防线、JWT 防抖、API Key 生命周期、N+1 消除与复合索引 | `thesis-assets/evidence/code-snippets/implementation-evidence-2026-05-31.md` | 已完成；可补充到第四章或答辩 |
| Bug 复盘 | Demo 代理错连、MySQL 启动前置校验 | `thesis-assets/evidence/bug-package-2026-04-24.md`、`thesis-assets/evidence/bug-evidence/` | 已完成 |
| 文献增强 | 文献质量评估、文献证据映射、参考文献工作库 | `thesis-assets/literature/literature-quality-review.md`、`thesis-assets/literature/literature-evidence-map.md`、`thesis-assets/literature/references-draft.bib` | 已完成；本轮已按正文首次引用顺序前置排序 |
| 答辩基础材料 | 演示路线、亮点、难点、追问问答、风险预案 | `thesis-assets/defense/defense-package-2026-04-25.md` | 已完成 |
| 答辩 PPT 模板 | 学校答辩 PPT 模板 | `thesis-assets/defense/贵州大学答辩PPT模板.pptx` | 当前未入库；需人工重新提供或从历史提交恢复 |
| 新增实现口径 | 简历解析输入长度保护、LLM 配置连通性测试、不足与展望补充 | `thesis-assets/drafts/chapter-04-implementation-draft.md`、`thesis-assets/drafts/chapter-05-testing-draft.md`、`thesis-assets/drafts/chapter-06-conclusion-abstract-draft.md` | 已补入章节草稿 |
| 封面与诚信责任书 | 封面页和诚信责任书已并入当前主稿 | `thesis-assets/毕业论文正式版（草稿）.docx` | 已补齐；仍需人工填写或确认签名、日期、指导教师等字段 |
| 真实公网 LLM 性能 | `TC-01` 到 `TC-11` 功能与公网 MIMO API 性能数据测试结果 | `thesis-assets/evidence/test-data/mimo-real-test-2026-05-27.md` | 已完成 |


## B. 本轮新增整合项

| 类别 | 内容 | 目标文件 | 状态 |
| --- | --- | --- | --- |
| 答辩页映射 | PPT 页码、页面主题、证据材料、对应论文章节和讲解要点 | `thesis-assets/defense/defense-slide-map.md` | 已补齐 |
| 正式讲稿 | 5-8 分钟答辩讲稿 | `thesis-assets/defense/defense-script-5-8min.md` | 已补齐 |
| 论文正式草稿版 | 前置页、摘要、自动目录、六章正文、参考文献与附录整合为 DOCX | `thesis-assets/毕业论文正式版（草稿）.docx` | 已补齐；仍需人工字段、目录域、页码和版式终审 |
| 当前 Word 工作稿 | 已完成 Markdown 润色基准稿真实回填的 Word 工作稿 | `thesis-assets/current/毕业论文正式版（润色回填）.docx` | 已完成；后续进入 Word 格式终审 |
| Markdown 润色基准稿 | 当前内容定版后的 Markdown 润色基准稿 | `thesis-assets/current/thesis-polished.md` | 已归档 |

## C. 仍需人工终审项

| 类别 | 当前口径 | 是否阻塞终稿 |
| --- | --- | --- |
| 封面人工字段 | 当前 DOCX 已包含封面页；指导教师、日期等字段需人工填写或确认 | 阻塞正式提交 |
| 诚信责任书签名与日期 | 当前 DOCX 已包含诚信责任书页；作者签名和日期需人工完成 | 阻塞正式提交 |
| 目录页码 | 当前 DOCX 已保留自动目录字段；正式提交前建议在 Word 中执行 `Ctrl+A` → `F9` 更新域并复核页码 | 阻塞正式提交 |
| 页码、页眉页脚、图题表题与参考文献格式 | 当前 Word 工作稿已完成真实回填；仍需逐页复核格式 | 阻塞正式提交 |

## D. 本轮移除项

| 类别 | 处理口径 | 说明 |
| --- | --- | --- |
| 毕业论文（设计）任务书模板 | 从当前终稿阻塞项中移除 | 已读取的论文设计管理办法当前未将任务书列入论文主稿装订顺序；除非学院或导师另行要求，不再作为当前主稿占位和补料项 |

## E. 预留待测项

| 类别 | 当前口径 | 是否阻塞终稿 |
| --- | --- | --- |
| 无 | 本轮已全部完成真实公网 LLM 性能与全链路测试验证 | 否 |

## F. 可选补充项

| 类别 | 建议 | 使用场景 |
| --- | --- | --- |
| DevTools Network 截图 | 如导师要求更强测试证据，可补接口耗时截图。 | 第五章或答辩追问 |
| 数据库截图 | 可补 `interview_message`、`interview_session` 表截图。 | 第四章实现或数据持久化说明 |
| 正式答辩 PPT | 人工重新提供或从历史提交恢复 PPT 模板后，基于 `thesis-assets/defense/defense-slide-map.md` 生成正式 PPT。 | 答辩前 |
| 参考文献导师确认 | 确认普通期刊、预印本和官方文档是否允许进入最终参考文献。 | 终稿前 |
