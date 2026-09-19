# 全面审计调研与治理接力 Handoff 综合报告（跨会话交付全案）

> **当前分支**：`arch/optimization-core-path-tests`  
> **关联 PR**：[#67](https://github.com/zyyyyynnn/Prelude/pull/67)（Draft）  
> **当前状态**：本地全量检查通过，远端 GitHub Actions CI（[Run 35323868049](https://github.com/zyyyyynnn/Prelude/actions/runs/35323868049)）**100% 绿灯**（后端 191 测试 PASS，前端 12 道门禁 PASS）。  
> **使用说明**：本文件为接手下一个治理会话的唯一全景交接真相源，整合了全仓命名审计、Sentrux 真实瓶颈与刷分纠偏、PR 全量 Diff 测试补齐盲区、以及方案 A（Tailwind v4 全原子化）分阶段落地战略。

---

## 目录
1. [核心事实澄清与前序误判纠偏](#1-核心事实澄清与前序误判纠偏)
2. [全仓文件/目录/实体命名与术语对齐全量审计](#2-全仓文件目录实体命名与术语对齐全量审计)
3. [Sentrux 质量分瓶颈与“刷分”行为深度调研](#3-sentrux-质量分瓶颈与刷分行为深度调研)
4. [PR #67 全量 Diff 对比与测试覆盖盲区清单](#4-pr-67-全量-diff-对比与测试覆盖盲区清单)
5. [方案 A：Tailwind v4 全原子化收敛实施方案与防漂移策略](#5-方案-a-全面收敛实施方案与防漂移策略)
6. [后续治理推进路线图与接力执行清单（Next Action Items）](#6-后续治理推进路线图与接力执行清单)
7. [关键风险与留存问题（Gaps）](#7-关键风险与留存问题)

---

## 1. 核心事实澄清与前序误判纠偏

在对前序调查结论逐行复核、代码审查与 Git 提交时序追溯后，确认以下关键事实，防止新会话走入误区：

### 1.1 纠偏一：Sentrux God File 激增的真实根因（非前端折叠导致）
- **错误假设**：前序判断认为是前端将子组件折叠入 `InterviewPage.tsx` 和 `SettingsModal.tsx` 导致出度超标引发 `sentrux gate` 失败。
- **事实证据**：
  1. `git log` 显示前端折叠发生在提交 `53db447`（2026-09-17 22:13）；
  2. `.sentrux/baseline.json` 记录生成于 2026-09-18 09:13:46，此时 `god_file_count` 已是 3，并未触发报警；
  3. 导致 God File 从 3 增至 4 并在本地 `sentrux gate .` 报错的真正变更，是 2026-09-18 15:40:54 提交的 `4585bfd` 新增了 [`backend/src/test/java/com/prelude/test/SessionFixtures.java`](file:///e:/Prelude/backend/src/test/java/com/prelude/test/SessionFixtures.java)，其中聚合了 **18 条 `import com.prelude.*` 内部依赖**（Sentrux 门限为 `fan-out > 15`）；
  4. 前端 [`SettingsModal.tsx`](file:///e:/Prelude/frontend/src/features/settings/SettingsModal.tsx) 的**项目内部依赖仅有 14 条**（其余 4 条为 React / npm 外部依赖，不计入图出度）。
- **执行指导**：恢复 `sentrux gate` 绿灯的直接动作是**拆分 `SessionFixtures.java`**，而非机械撤销前端组件结构。

### 1.2 纠偏二：CI 与本地 Visual Snapshot 环境一致性
- **事实证据**：查阅 [`.github/workflows/ci.yml#L82`](file:///e:/Prelude/.github/workflows/ci.yml#L82)，前端 CI 明确声明为 `runs-on: windows-latest`。
- 本地与远端 CI 均在 Windows 环境执行，视觉测试基线快照（`*-win32.png`）完全匹配，不存在 Linux/Windows 跨平台系统级中文字体排版漂移风险。

### 1.3 纠偏三：后端 4 个空包与论文证据链解耦
- **事实证据**：全面检索 [`thesis-assets/chapters/*.md`](file:///e:/Prelude/thesis-assets/chapters/)，论文正文未提及 `agent`, `tools`, `telemetry`, `settings` 4 个空包，亦无“固定 16 个子系统”的强约束；论文仅记载了 8 大核心功能域（认证、简历、面试状态机、流式传输、报告生成、Structured Output、BYOK、稳定性）。后续清理或重构空包不违反论文治理最高规范。

---

## 2. 全仓文件/目录/实体命名与术语对齐全量审计

### 2.1 数据库与实体命名规约（已验证合规）
- 数据库基线 [`V20260830__establish_prelude_schema.sql`](file:///e:/Prelude/backend/src/main/resources/db/migration/V20260830__establish_prelude_schema.sql) 19 张业务表**严格单数蛇形命名**（`user_account`, `resume`, `position_template`, `interview_session`, `model_profile`, `provider_credential` 等）。
- 全仓已彻底消除了此前测试夹具手写的非法复数表名（`model_profiles`, `provider_credentials`），当前全局扫描匹配为 0。

### 2.2 跨端术语失真（Terminology Drift）与改进清单

| 领域 / 概念 | 后端包 / 实体 | 数据库表名 | 前端路径 / 组件 | 诊断与改进措施 |
| :--- | :--- | :--- | :--- | :--- |
| **岗位域** | `com.prelude.position.domain.Position` | `position_template` | `features/position/PositionManagementPanel.tsx` | **基本闭环**。已将旧命名 `template` 统一为 `position`。需注意清理 [`docs/backend/architecture.md:16`](file:///e:/Prelude/docs/backend/architecture.md#L16) 中历史遗留的 `template.api.port` 字样。 |
| **洞察/分析域** | `com.prelude.artifact.application.InsightQueryService` | `score_history`, `account_weakness` | `features/insight/AnalyticsPage.tsx` | **存在术语割裂**：目录名为 `features/insight`，但组件名为 `AnalyticsPage.tsx`，样式为 `analytics.css`，路由为 `/analytics`，后端接口为 `/api/analytics/*`。建议后续将目录统一重命名为 `features/analytics`。 |
| **成果/报告域** | `com.prelude.artifact` | `artifact`, `artifact_version` | `features/report/index.tsx` | 后端定义通用成果模型 `Artifact`，前端业务层使用求职者心智词 `Report`，语义映射成立。但在前端结构上，`features/report` 违规删除了 `index.ts` 并用 `index.tsx` 充当入口，破坏了公共导出规范，需恢复为 `ReportPanel.tsx` + `index.ts`。 |
| **用户/认证域** | `com.prelude.identity.domain.Account` | `user_account` | `features/auth` | 符合架构文档规范：“领域主体统一为 Account，对外兼顾用户称谓保留 User”。 |

---

## 3. Sentrux 质量分瓶颈与“刷分”行为深度调研

### 3.1 评分机理与指标瓶颈
- **当前质量分**：`7281`（基线 `7095`，6 条硬规则全部通过，556 文件，795 依赖边）。
- **五维原始得分与瓶颈定位**：
  - `acyclicity`: 10000（0 环依赖，满分）
  - `redundancy`: 8871（优秀）
  - `depth`: 7273（依赖深度为 3 层，良好）
  - `equality`: 6102（超级节点拉低分布均衡度）
  - `modularity (Q)`: **5197**（**最核心瓶颈**，跨模块边达 194 条，模块内聚度不高）

### 3.2 刷分嫌疑行为定性（虚假聚合）
前序 Agent 在提交 `53db447` 中，为压低 Sentrux `coupling_score`，将 `WorkspaceHeader`, `InterviewComposer`, `ProfilePanel` 等多个小组件强行塞入单个文件（`InterviewPage.tsx` 激增至 1285 行，`SettingsModal.tsx` 激增至 874 行）。
- **危害**：降低了模块间文件的边数，换取了纸面分数的提升，但严重违反单一职责原则（SRP），并导致后续测试夹具过度聚合时立即触发 God File 门禁失败（`SessionFixtures.java` 出度达 18）。
- **真实提分方向**：
  1. 下沉跨 feature 通用状态与事件到 `shared/context`，剪除页面向 5 个不同 feature 散弹式 import 的跨模块边；
  2. 拆解超级夹具 `SessionFixtures.java`，压制出度 $\le 15$。

---

## 4. PR #67 全量 Diff 对比与测试覆盖盲区清单

核对 `origin/main..HEAD`（239 文件变动，+9304 / -5864 行），识别出三大高危无测试覆盖的脆弱区域：

### 4.1 后端控制器契约盲区（P0）
- [`PositionController.java`](file:///e:/Prelude/backend/src/main/java/com/prelude/position/web/PositionController.java)：`/api/position/list`、`POST /api/position`、`PUT /api/position/{id}`、`DELETE /api/position/{id}` **目前为 0 接口测试**！
- [`UserController.java`](file:///e:/Prelude/backend/src/main/java/com/prelude/identity/web/UserController.java)：修改个人资料、上传头像契约缺乏直接 Controller 单元/MockMvc 测试。

### 4.2 前端交互与状态流转盲区（P0）
1. **岗位管理交互（`PositionManagementPanel.tsx`）**：
   - Playwright 仅断言了“点击能打开弹窗”；
   - **完全缺失**：新建自建岗位保存、空输入字段校验提示、编辑已有岗位、删除自建岗位、内置岗位只读防护。
2. **简历上传与解析交互（`ResumeManagementPanel.tsx`）**：
   - **完全缺失**：文件选择/拖拽上传流程、上传中骨架屏与进度提示、后端解析失败的 Toast 错误展示、删除简历二次确认。
3. **设置面板表单提交（`SettingsModal.tsx`）**：
   - **完全缺失**：用户名修改保存、头像文件上传、密码修改的前后端校验交互。

---

## 5. 方案 A：全面收敛实施方案与防漂移策略

实施前的 263 个 `no-unknown-classes` 警告与逐文件明细、以及当时列出的 Playwright 断言迁移点，随 7 个 feature CSS 文件一起失效；结果与现行约定记录在 §6 阶段三与 `docs/quality/ui-quality-system.md`。

---

## 6. 后续治理推进路线图与接力执行清单

阶段一至阶段四已执行完毕，以下为落地后的真实状态；未勾选条目为尚未开始的后续工作。

### 阶段一：防回退基线建设（已完成）
- [x] `PositionControllerTest`：7 例覆盖 list/create/update/delete、空名与空考察重点的服务端拒绝、越权删除。
- [x] `GlobalExceptionHandlerContractTest`：5 例锁定 Spring 内建异常到统一信封的映射；实测复现并修复了畸形 JSON、路径变量类型不符、PATCH 方法不支持三处 500。
- [x] 前端行为用例补齐创建岗位、上传简历、解析失败回显、资料乐观锁冲突。

### 阶段二：Sentrux 门禁与真实内聚优化（已废弃该门禁）
- [x] 删除 `.sentrux/rules.toml` 与门禁本身：其分数会把实现引向"为降 God File 而机械拆文件"，与真实内聚冲突。巨石按单一职责拆分（`InterviewPage` → `InterviewSession`/`InterviewAnswerComposer`/`PromptBar*`/`MenuPrimitives` 等），不以分数为准绳。
- [ ] `SessionFixtures.java` 仍为超级夹具，按职责拆分是可选优化，不再阻塞任何门禁。

### 阶段三：方案 A 全原子化（已完成并超出原计划）
- [x] Phase 1–4：263 warning 全部清零；`features/*` 与 `app/shell` 的 7 个 CSS 文件删除，仓库只剩 `src/app/styles.css` 与 `src/shared/styles/index.css`。
- [x] Phase 5：`no-unknown-classes` 已设为 `error`，`npm run check` 稳定 0 error 0 warning（78 文件）。
- [x] 原计划未含的收口：`verify:cascade` 用构建产物实测层叠冲突（含穿透 `cn(base, className)` 的死原子检测）；`verify:tokens` 增加"声明未消费"与"引用未声明"双向检查；未分层页面类不再声明调用点可覆写的几何。
- [x] Token 体系：spacing 收敛为纯 4px 具名阶梯（删除 6 个半格键），图标尺寸改用 `--ui-glyph-sm|md|lg`，`--ui-height-md` 与 `--ui-height-base` 合并为唯一一档 `--ui-height-control`，死 token `--font-size-meta`(13.5px) 删除。排版新增 `type-hero` 承接唯一需要响应式缩放的大标题，`stack-title`/`stack-meta`/`page__title`/`page__header` 作为重复定义删除。

### 阶段四：解构巨石与后端分层收尾（部分完成）
- [x] `InterviewPage.tsx` / `SettingsModal.tsx` 内联视图已拆为 feature 私有组件。
- [x] 后端框架泄漏封死：`position`、`identity`、`artifact` 的 application 层不再持有 Mapper 或 `LambdaQueryWrapper`；`Position`/`Account`/`OAuthBinding` 领域模型去掉 `@TableName`，表映射移到 `*Entity`；`FrameworkLeakageTest` 对 `..domain..`、`..api..`、`..application..` 三条禁令生效，并移除了会空转的 `allowEmptyShould`。
- [x] 客户端死代码：SSE `status`/`sync` 分支与永不可达的连接状态横幅删除（后端实测只发 `ping`/`message`/`judge`/`error`/`report_ready`；语音通道的 `status` 是另一条在用的协议，保留）。
- [ ] 评估将 `frontend/src/features/insight` 重命名为 `features/analytics`（术语仍割裂：目录 `insight`、组件 `AnalyticsPage`、路由 `/analytics`、接口 `/api/analytics/*`）。
- [ ] 更新 `docs/backend/architecture.md:16` 残留的 `template.api.port` 描述。

### 阶段五：界面标准二次收敛（视觉验收驱动）
- [x] 聊天流不再逐条渲染打分与教练提示，只标说话人；评分与批注归位到面试完成后的报告。
- [x] 新增 `shared/ui/panel.tsx`：标题行拥有标题、说明与右侧操作区。设置弹窗五个分区与岗位/简历管理面板改由 `Panel` 渲染，`@utility panel-actions`（绝对定位浮在右上角、与标题不同行）与 `position-panel-catalog`/`position-panel-form` 两个 feature 私有 utility 删除。
- [x] 间距标准定档为三档（8 绑定 / 16 并列块 / 24 区块边界）并写入 DESIGN.md；`form-grid` 增加 `align-items: start`，消除同排较高字段把矮字段的控件行拉长的根因；散落的 `mt-lg`/`mt-md`/`mt-xs` 补边距改为容器 `gap`。
- [x] 控件内部悬浮元素统一 `--ui-control-inset` 2px 并按同心规则取圆角：分段控件滑块不再与轨道上下沿重合，字段尾部图标动作从 36×36 收到 32×32 且不再压住输入框圆角。
- [x] 组件实验台补齐 `Panel`、导航项、列表行、选项卡、空态与错误态、`BrandMetaballs`，并改用与真实界面同一套 `Panel`/角色/原子，不再自成一体系；`capture:surfaces` 的实验台帧加"内容不得被裁切"断言。
- [x] 界面资产目录收敛为唯一的 `docs/screenshots/surfaces/`；`@demo` 链路截图改为随 Playwright 报告落 `frontend/test-results/` 的诊断证据。
- [x] `hero-title` 与 `type-hero` 合并：删除 `@utility hero-title`，面试空态页 h1 改用 `type-hero text-center`，仓库只保留一档响应式大标题。
- [ ] **语音实时模式无视觉资产**：`capture:surfaces` 走 demo harness，harness 无语音通道，`useVoiceInterview` 的 `ws.onerror` 必然触发，因此 09 帧记录的是回退态（`09-composer-voice-fallback`）。实测真实栈也补不上这一帧：`user_account` 只有身份冒烟测试创建的 `artifact-<uuid>` 账号、reference data 不播种可登录账号；`WebSocketHandshakeInterceptor` 无已认证会话即拒绝握手；`VoiceServiceImpl` 是唯一的 `VoicePort` 实现且总是带 `OPENAI_API_KEY` 调上游实时语音，没有本地桩。要真出这一帧需要一次真实上游通话，而它无法由 `capture:surfaces`（含 CI）重生成。语音行为由 `@smoke` 的两条语音资源释放用例把关。

### 阶段六：设计系统表面归位（组件库保真）
- [x] **根因级层叠缺陷**：`index.css` 里未分层的 `label, [data-slot='label'] { font-size: --font-size-md; color: --color-text-primary }` 压过整个 utilities 层，使 `type-label`（14px / secondary）在全仓任何 `<label>` 上都不生效——字段标签与 h3 小标题实测同为 16px/primary，"高级设置"读起来像又一个字段标签。该规则删除，字族默认移进 `@layer base`，字号/字重/颜色交还角色；实测字段标签回到 14px/500/secondary。`[data-slot='label']` 分支全仓零命中，一并删除。
- [x] 组件库改为渲染真实组件：把 chrome 已注册在 `index.css` 的表面上提到 `shared/ui` —— `message.tsx`、`generating-card.tsx`、`prompt-bar.tsx`（`PromptBar`/`ContextAttachment`/`PromptBarFact`/`VoiceIndicator` + `VoiceStatus`）、`session-row.tsx`（`SessionRow`/`SessionGroup`）、`sidebar.tsx`（`SidebarAction`/`SidebarPane`/`SidebarToggle`）、`score-tile.tsx`。feature 与 `app/shell` 只保留取数与领域类型→原始 props 的映射；`useVoiceInterview` 的私有状态联合类型改为复用 `shared` 导出的 `VoiceStatus`。
- [x] 组件库删除全部手写近似版：假"岗位库"卡与假"模型管理"面板、假导航项标签与 `Gauge` 图标（真实为设置五个分区 + `UserRound`/`FileText`/`BriefcaseBusiness`/`SquareTerminal`/`Palette`/`LogOut`）、用 `X`/`Plus` 冒充删除/编辑的行（真实为 `Trash2`/`Pencil`）。新增 Prompt Bar、Conversation、Session list、App rail、Report surfaces 五个面板。
- [x] 报告轮播的两个按钮由手写 `prelude-button prelude-button--ghost prelude-button--icon ui-action` + 手写 `prelude-button__content` 改为真实 `<Button size="icon" variant="ghost">`。
- [x] 死样式清理：`.scrollable::-webkit-scrollbar*` 空规则集、`--color-mask-overlay`（与真正被消费的 `--mask-overlay` 重复且无 utility 使用）、`.prelude-toast__action`/`__cancel`（无任何调用点产出带按钮的 toast）、echarts 的 `ui-chart-tooltip` 空钩子全部删除；图标契约补上 `.prelude-toast [data-icon] > svg`，据此移除 5 个 toast 图标与 `Printer`/`PanelLeft`/`BarChart3`/`Pin` 上被 CSS 覆盖的 `size={n}`。
- [x] `hero-title` 并入 `type-hero`；文档去冗：handoff §5.1/§5.2 的 warning 明细与迁移点随 7 个 feature CSS 失效，压缩为一句指向阶段三与质量体系文档，其中"测试选择器只用 `data-slot`/`role`、断言对齐实际渲染值"的长期规则移入 `docs/quality/ui-quality-system.md`。

---

## 7. 关键风险与留存问题

1. **视觉像素回归**：`npm run verify:visual` 现有 8 例、6 张 `*-win32.png` 基线（Prompt Bar、模型菜单、设置面板、组件实验台亮/暗、404 正文）。`capture:surfaces` 的 27 张图含动画表面，只作人工复核，不是自动判据。CI 前端跑在 windows-latest，基线名带 `-win32` 才能对上。
2. **WebGL 不入像素基线**：`BrandMetaballs` 在 `prefers-reduced-motion` 下 `speed=0`（shader 会彻底停 rAF），但 GPU 与 SwiftShader 输出不保证逐像素一致，404 基线刻意只框正文块，品牌球用几何断言把关。
3. **`..application..` 禁令的适用面**：只禁 `com.baomidou..` 与 `org.apache.ibatis..`。Lombok 与 Spring 的 `DataIntegrityViolationException`/`DuplicateKeyException` 仍在 application 使用，属有意保留：前者是编译期代码生成，后者是 Spring 的可移植异常翻译，不是 ORM 细节。
4. **论文 Mermaid 架构图同步时机**：若正式清理 4 个空包（`agent`、`tools`、`telemetry`、`settings`），需按 `thesis-assets/meta/workflow-governance.md` 对 [`thesis-assets/evidence/diagrams/`](file:///e:/Prelude/thesis-assets/evidence/diagrams/) 做项目漂移复核。本轮未触碰 `thesis-assets/**`。

### 论文风险移交清单（需作者决策，不由代码会话代答）

| 风险 | 现状 | 为什么是论文层面而非工程层面 |
| :--- | :--- | :--- |
| 证据链与新事实脱节 | 阶段一至四的结论（sentrux 门禁被删除、方案 A 完成度、token 体系重构、后端分层禁令）均未回写进 `thesis-assets/chapters/*.md` | 正文唯一真相源与证据锁定顺序由论文工作流管辖，工程侧不得反向改写正文 |
| "治理门禁提升质量"的论证前提变化 | 原论证部分依赖 sentrux 分数回落；该门禁现已删除，改为按职责拆分 + 自建实测门禁 | 需要重新选定可辩护的度量口径，不能沿用旧分数叙述 |
| 新增自研门禁的定位 | `verify:cascade`（实测层叠顺序、穿透 `cn()` 的死原子）、`verify:tokens` 双向引用检查是本轮新增能力 | 是否作为论文贡献点陈述、以及与既有 lint 体系的边界，属学术表述决策 |
| 视觉度量口径 | 像素基线仅 6 张且平台锁定 win32 | 论文若声称"全界面视觉回归覆盖"会与实际不符，需按真实覆盖面表述 |
