# 最终证据锁版记录

> 本文件只登记已采集、已确认、可追溯的论文与答辩证据。若与 dated 阶段记录冲突，以本文件和 `thesis-control.md` 为准。

| 素材类型 | 素材名称 | 采集日期 | 对应代码版本标识 | 是否已同步到论文 | 是否已同步到 PPT | 是否已同步到答辩讲解 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 截图 | `docs/images/login.png` | 2026-04-28 | 本机 Demo Twin 截图重跑结果 | README 已同步；正式草稿版 DOCX 由人工侧自行处理 | 否 | 否 | 登录页展示图；不等同于学校终稿正文已选用 |
| 截图 | `docs/images/interview-empty.png` | 2026-04-28 | 本机 Demo Twin 截图重跑结果 | README 已同步；正式草稿版 DOCX 由人工侧自行处理 | 否 | 否 | 主工作台（未开始面试的空状态）展示图；不等同于学校终稿正文已选用 |
| 截图 | `docs/images/interview-workbench.png` | 2026-04-28 | 本机 Demo Twin 截图重跑结果 | README 已同步；正式草稿版 DOCX 由人工侧自行处理 | 否 | 否 | 主工作台（报告预览）展示图；已覆盖多岗位 Demo 会话；不等同于学校终稿正文已选用 |
| 截图 | `docs/images/analytics.png` | 2026-04-28 | 本机 Demo Twin 截图重跑结果 | README 已同步；正式草稿版 DOCX 由人工侧自行处理 | 否 | 否 | 数据看板展示图；不等同于学校终稿正文已选用 |
| 图表 | 图3.1 系统核心用例图 | 2026-04-28 | `thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png` | 是 | 否 | 否 | 由 `fig-3.1-core-use-case.mmd` 渲染生成 |
| 图表 | 图3.2 数据库 E-R 图 | 2026-04-28 | `thesis-assets/evidence/diagrams/fig-3.2-database-er.png` | 是 | 否 | 否 | 由 `fig-3.2-database-er.mmd` 渲染生成 |
| 图表 | 图3.3 系统整体架构图 | 2026-04-28 | `thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png` | 是 | 否 | 否 | 由 `fig-3.3-system-architecture.mmd` 渲染生成 |
| 测试数据 | `thesis-assets/evidence/test-data/env-2026-04-24.md` | 2026-04-24 | 本机验证结果 | 是 | 否 | 否 | 已含环境、后端测试 and 前端构建 |
| 测试数据 | `thesis-assets/evidence/test-data/demo-2026-04-25.md` | 2026-04-25 | Demo Twin 本机业务测试 | 是 | 否 | 否 | `TC-01` 到 `TC-09` 通过；含 SSE TTFB 与 PDF 接口耗时；不代表真实公网 LLM 性能 |
| 测试数据 | `thesis-assets/evidence/test-data/demo-2026-04-25.json` | 2026-04-25 | Demo Twin 本机业务测试 | 否 | 否 | 否 | 结构化测试指标与数据记录；与 `.md` 同源 |
| 测试数据 | `thesis-assets/evidence/test-data/mimo-2026-05-27.md` | 2026-05-27 | MIMO 真实公网全链路测试 | 否 | 否 | 否 | `TC-01` 到 `TC-11` 通过；含真实公网 MIMO API 响应性能数据与逻辑缺陷修复验证 |
| 测试数据 | `thesis-assets/evidence/test-data/mimo-2026-05-27.json` | 2026-05-27 | MIMO 真实公网全链路测试 | 否 | 否 | 否 | 全链路结构化测试指标与数据记录 |
| 实现证据 | `thesis-assets/evidence/code-snippets/impl-2026-04-24.md` | 2026-04-24 | 当前代码结构 | 是 | 否 | 否 | 已同步到第四章实现证据来源 |
| 实现证据 | `thesis-assets/evidence/code-snippets/impl-2026-05-31.md` | 2026-05-31 | 安全加固与性能优化后代码 | 否 | 否 | 否 | 含 LLM 柔性降级、PDF 安全防线、JWT 防抖、API Key 生命周期、N+1 消除与复合索引；可补充到第四章或答辩 |
| 实现证据 | `thesis-assets/evidence/code-snippets/impl-2026-06-02.md` | 2026-06-02 | Roadmap架构高可用与体验升级全面落地代码 | 否 | 否 | 否 | 含结构化输出、SSE重连、Redis、语音流、熔断与监控；可补充到第四章或答辩 |
| Bug 记录 | `thesis-assets/evidence/bug-evidence/package-2026-04-24.md` | 2026-04-24 | 当前开发过程记录 | 是 | 否 | 是 | 已选 Bug 1 与 Bug 2 作为论文/答辩主讲候选 |
| 文献证据 | `thesis-assets/literature/quality-review.md` | 2026-04-26 | 本轮文献增强 | 是 | 否 | 否 | 记录候选文献筛选依据和采用状态 |
| 文献证据 | `thesis-assets/literature/evidence-map.md` | 2026-04-26 | 本轮文献增强 | 是 | 否 | 否 | 记录文献与论文章节的落位关系 |
| 答辩材料 | `thesis-assets/defense/slide-map.md` | 2026-04-25 | 当前答辩资料结构 | 正式草稿版 DOCX 不纳入正文，仅作为答辩材料来源 | 是 | 是 | PPT 页级映射表 |
| 答辩材料 | `thesis-assets/defense/script.md` | 2026-04-25 | 当前答辩资料结构 | 正式草稿版 DOCX 不纳入正文，仅作为答辩材料来源 | 否 | 是 | 5-8 分钟正式讲稿 |
| 答辩模板 | `thesis-assets/defense/贵州大学答辩PPT模板.pptx` | 2026-04-26 | 学校答辩 PPT 模板 | 否 | 否 | 否 | 当前未入库；需人工重新提供或从历史提交恢复 |
| 历史整合文档 | `thesis-assets/毕业论文资料整合.docx` | 2026-04-25 | 历史论文资料快照 | 否 | 否 | 否 | 历史中已删除，当前不恢复，不作为当前状态依据 |
| 原始主稿 / 格式母版 | `thesis-assets/毕业论文正式版（草稿）.docx` | 2026-04-26 | 原始论文草稿结构 | 是 | 否 | 否 | 保留不覆盖；封面和诚信责任书已并入，签名、日期、指导教师和目录页码仍需人工终审 |
| 当前 Word 工作稿 | `thesis-assets/meta/school-template.docx` | 2026-06-05 | 已迁移为 school-template.docx 作为排版样式基准模板 | 是 | 否 | 否 | 后续进入 Word 目录域更新、页码、页眉页脚、图题表题和参考文献格式终审 |
| Markdown 润色基准稿 | `thesis-assets/thesis-full.md` | 2026-06-05 | PaperSpine 全章节强力重构合并版基准稿 | 是 | 否 | 否 | 物理合并内容定版后的 Markdown 基准稿 |
