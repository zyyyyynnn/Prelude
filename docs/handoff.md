# 全面审计调研与治理接力 Handoff 综合报告（跨会话交付全案）

> **当前分支**：`arch/optimization-core-path-tests`
>
> **关联 PR**：[#67](https://github.com/zyyyyynnn/Prelude/pull/67)（Draft）
>
> **当前状态**：本地全量检查在当前 HEAD 通过——后端 255 个 `@Test`（58 个类，其中 15 个按环境变量启用；四个依赖服务在跑时 0 跳过），前端 12 道 CI 门禁同义命令全绿，`verify:visual` 22 例 / 44 张 `*-win32.png` 基线。
>
> **远端状态**：`origin/arch/optimization-core-path-tests` 与本地同步，`4585bfd..` 这一段已第一次真正进入 GitHub Actions。三次 run：`35523034826` 红在 `test:smoke`（判据赌时序）、`35523691947` 红在 `verify:visual`（判据浏览器是环境属性），`35528454272` 起 backend 与 frontend 双双通过。**教训不是"CI 会噪声"，两次红都是真缺陷**：本机 41 例全绿、22 例全绿的判据，换一台机器就不成立（见阶段十六末条与阶段十七）。
>
> **使用说明**：本文件为接手下一个治理会话的全景交接真相源。§1–§4 是当时的调研与诊断，其中 sentrux 分数与 PR #67 diff 两节已标为历史存档，结论以 §6 各阶段记录为准。

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
- **当时的执行指导**：恢复 `sentrux gate` 绿灯的直接动作是**拆分 `SessionFixtures.java`**，而非机械撤销前端组件结构。该门禁随后被删除（§6 阶段二），这条指导已无对象；`SessionFixtures` 现为 240 行 / 18 条内部 import，按职责拆分是可选优化，不阻塞任何检查。

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
| **岗位域** | `com.prelude.position.domain.Position` | `position_template` | `features/position/PositionManagementPanel.tsx` | **基本闭环**。已将旧命名 `template` 统一为 `position`。 |
| **洞察/分析域** | `com.prelude.artifact.application.InsightQueryService` | `score_history`, `account_weakness` | `features/analytics/AnalyticsPage.tsx` | **已闭环**：目录 `insight` 曾与组件 `AnalyticsPage`、路由 `/analytics`、接口 `/api/analytics/*` 割裂，前端目录已在阶段七b 统一为 `features/analytics` 并补 barrel。后端 `InsightQueryService` 有意保留——它是洞察域的领域服务名，与前端展示路由不必同名。 |
| **成果/报告域** | `com.prelude.artifact` | `artifact`, `artifact_version` | `features/report/index.ts` | 后端定义通用成果模型 `Artifact`，前端业务层使用求职者心智词 `Report`，语义映射成立。前端入口曾以 `index.tsx` 同时充当 barrel 与全部视图，现已按职责拆为 `parse.ts` + `report-view.tsx` + `report-sections.tsx` + `print.ts`，`index.ts` 为纯再导出（§6 阶段八）。 |
| **用户/认证域** | `com.prelude.identity.domain.Account` | `user_account` | `features/auth` | 符合架构文档规范：“领域主体统一为 Account，对外兼顾用户称谓保留 User”。 |

---

## 3. Sentrux 质量分瓶颈与“刷分”行为深度调研

> **历史存档**：本节记录的是门禁被删除**之前**的调研。`.sentrux/` 与 `sentrux gate` 已在阶段二移除（见 §6），下列分数、文件数与依赖边数不再由任何工具产出，只作为当时的判断依据保留。现行结构约束是 `verify:architecture` 与按职责拆分，不以任何质量分为准绳。

### 3.1 评分机理与指标瓶颈
- **当时的质量分**：`7281`（基线 `7095`，6 条硬规则全部通过，556 文件，795 依赖边）。
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

> **历史存档 + 现行处置**：核对时 `origin/main..HEAD` 为 239 文件（+9304 / −5864）；该分支现已推进到 396 文件（+16694 / −9415）。下面列出的盲区**全部已在阶段一关闭**，保留原文是为了说明覆盖是按哪些缺口补的。

### 4.1 后端控制器契约盲区（P0）— 已关闭
- `PositionController` 的 list/create/update/delete 四端点：当时 0 接口测试，现由 `position/web/PositionControllerTest` 7 例覆盖（见 §6 阶段一）。
- `UserController` 的资料修改与头像上传：当时缺直接契约测试，现由 `identity/web/UserControllerContractTest` 5 例覆盖，含校验拒绝、畸形邮箱与 revision 冲突。
- **本轮复核新增的同类缺口**（当时未列出，因为不在 PR #67 的 diff 里）：`InterviewController` 九个端点、`ResumeController` 三个、`AnalyticsController` 三个、`AttachmentController` 两个、`JobController` 两个仍为 0 HTTP 层测试——领域与应用层有用例，HTTP 契约没有。

### 4.2 前端交互与状态流转盲区（P0）— 已关闭
岗位管理的新建/编辑/删除与内置只读防护、简历上传与解析失败回显、设置面板的资料与密码提交，现由 `tests/app.management.spec.ts` 覆盖。
- **本轮复核新增的同类缺口**：主题选择与保存、头像上传、密码显隐、简历删除、逐题复盘轮播、产品内生成态在 `tests/` 全文零命中。

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
- [x] Phase 5：`no-unknown-classes` 已设为 `error`，`npm run check` 稳定 0 error 0 warning（当时 78 文件，现为 96 个受检文件 / 81 个 `src` 下 ts·tsx）。
- [x] 原计划未含的收口：`verify:cascade` 用构建产物实测层叠冲突（含穿透 `cn(base, className)` 的死原子检测）；`verify:tokens` 增加"声明未消费"与"引用未声明"双向检查；未分层页面类不再声明调用点可覆写的几何。
- [x] Token 体系：spacing 收敛为纯 4px 具名阶梯（删除 6 个半格键），图标尺寸改用 `--ui-glyph-sm|md|lg`，`--ui-height-md` 与 `--ui-height-base` 合并为唯一一档 `--ui-height-control`，死 token `--font-size-meta`(13.5px) 删除。排版新增 `type-hero` 承接唯一需要响应式缩放的大标题，`stack-title`/`stack-meta`/`page__title`/`page__header` 作为重复定义删除。

### 阶段四：解构巨石与后端分层收尾（部分完成）
- [x] `InterviewPage.tsx` / `SettingsModal.tsx` 内联视图已拆为 feature 私有组件。
- [x] 后端框架泄漏封死：`position`、`identity`、`artifact` 的 application 层不再持有 Mapper 或 `LambdaQueryWrapper`；`Position`/`Account`/`OAuthBinding` 领域模型去掉 `@TableName`，表映射移到 `*Entity`；`FrameworkLeakageTest` 对 `..domain..`、`..api..`、`..application..` 三条禁令生效，并移除了会空转的 `allowEmptyShould`。
- [x] 客户端死代码：SSE `status`/`sync` 分支与永不可达的连接状态横幅删除（后端实测只发 `ping`/`message`/`judge`/`error`/`report_ready`；语音通道的 `status` 是另一条在用的协议，保留）。

### 阶段五：界面标准二次收敛（视觉验收驱动）
- [x] 聊天流不再逐条渲染打分与教练提示，只标说话人；评分与批注归位到面试完成后的报告。
- [x] 新增 `shared/ui/panel.tsx`：标题行拥有标题、说明与右侧操作区。设置弹窗五个分区与岗位/简历管理面板改由 `Panel` 渲染，`@utility panel-actions`（绝对定位浮在右上角、与标题不同行）与 `position-panel-catalog`/`position-panel-form` 两个 feature 私有 utility 删除。
- [x] 间距标准定档为三档（8 绑定 / 16 并列块 / 24 区块边界）并写入 DESIGN.md；`form-grid` 增加 `align-items: start`，消除同排较高字段把矮字段的控件行拉长的根因；散落的 `mt-lg`/`mt-md`/`mt-xs` 补边距改为容器 `gap`。
- [x] 控件内部悬浮元素统一 `--ui-control-inset` 2px 并按同心规则取圆角：分段控件滑块不再与轨道上下沿重合，字段尾部图标动作从 36×36 收到 32×32 且不再压住输入框圆角。
- [x] 组件实验台补齐 `Panel`、导航项、列表行、选项卡、空态与错误态、`BrandMetaballs`，并改用与真实界面同一套 `Panel`/角色/原子，不再自成一体系；`capture:surfaces` 的实验台帧加"内容不得被裁切"断言。
- [x] 界面资产目录收敛为唯一的 `docs/screenshots/surfaces/`；`@demo` 链路截图改为随 Playwright 报告落 `frontend/test-results/` 的诊断证据。
- [x] `hero-title` 与 `type-hero` 合并：删除 `@utility hero-title`，面试空态页 h1 改用 `type-hero text-center`，仓库只保留一档响应式大标题。
- [ ] **语音实时模式无视觉资产**：`capture:surfaces` 走 demo harness，harness 无语音通道，`useVoiceInterview` 的 `ws.onerror` 必然触发，因此 09 帧记录的是回退态（`09-composer-voice-fallback`）。实测真实栈也补不上这一帧：`user_account` 只有身份冒烟测试创建的 `artifact-<uuid>` 账号、reference data 不播种可登录账号；`WebSocketHandshakeInterceptor` 无已认证会话即拒绝握手；`VoiceServiceImpl` 是唯一的 `VoicePort` 实现且总是带 `OPENAI_API_KEY` 调上游实时语音，没有本地桩。要真出这一帧需要一次真实上游通话，而它无法由 `capture:surfaces`（含 CI）重生成。语音行为由 `@smoke` 的两条语音资源释放用例把关。**（已解决：阶段八用 `installVoiceLane` 假掉传输与音频端，产出连接/聆听/处理/播报/回退五帧；上游音质仍无资产，也仍不可在 CI 重生成。）**

### 阶段六：设计系统表面归位（组件库保真）
- [x] **根因级层叠缺陷**：`index.css` 里未分层的 `label, [data-slot='label'] { font-size: --font-size-md; color: --color-text-primary }` 压过整个 utilities 层，使 `type-label`（14px / secondary）在全仓任何 `<label>` 上都不生效——字段标签与 h3 小标题实测同为 16px/primary，"高级设置"读起来像又一个字段标签。该规则删除，字族默认移进 `@layer base`，字号/字重/颜色交还角色；实测字段标签回到 14px/500/secondary。`[data-slot='label']` 分支全仓零命中，一并删除。
- [x] 组件库改为渲染真实组件：把 chrome 已注册在 `index.css` 的表面上提到 `shared/ui` —— `message.tsx`、`generating-card.tsx`、`prompt-bar.tsx`（`PromptBar`/`ContextAttachment`/`PromptBarFact`/`VoiceIndicator` + `VoiceStatus`）、`session-row.tsx`（`SessionRow`/`SessionGroup`）、`sidebar.tsx`（`SidebarFrame`/`SidebarAction`/`SidebarPane`/`SidebarToggle`）、`score-tile.tsx`。feature 与 `app/shell` 只保留取数与领域类型→原始 props 的映射；`useVoiceInterview` 的私有状态联合类型改为复用 `shared` 导出的 `VoiceStatus`。
- [x] 组件库删除全部手写近似版：假"岗位库"卡与假"模型管理"面板、假导航项标签与 `Gauge` 图标（真实为设置五个分区 + `UserRound`/`FileText`/`BriefcaseBusiness`/`SquareTerminal`/`Palette`/`LogOut`）、用 `X`/`Plus` 冒充删除/编辑的行（真实为 `Trash2`/`Pencil`）。新增 Prompt Bar、Conversation、Session list、App rail、Report surfaces 五个面板。
- [x] 报告轮播的两个按钮由手写 `prelude-button prelude-button--ghost prelude-button--icon ui-action` + 手写 `prelude-button__content` 改为真实 `<Button size="icon" variant="ghost">`。
- [x] 死样式清理：`.scrollable::-webkit-scrollbar*` 空规则集、`--color-mask-overlay`（与真正被消费的 `--mask-overlay` 重复且无 utility 使用）、`.prelude-toast__action`/`__cancel`（无任何调用点产出带按钮的 toast）、echarts 的 `ui-chart-tooltip` 空钩子全部删除；图标契约补上 `.prelude-toast [data-icon] > svg`，据此移除 5 个 toast 图标与 `Printer`/`PanelLeft`/`BarChart3`/`Pin` 上被 CSS 覆盖的 `size={n}`。
- [x] `hero-title` 并入 `type-hero`；文档去冗：handoff §5.1/§5.2 的 warning 明细与迁移点随 7 个 feature CSS 失效，压缩为一句指向阶段三与质量体系文档，其中"测试选择器只用 `data-slot`/`role`、断言对齐实际渲染值"的长期规则移入 `docs/quality/ui-quality-system.md`。
- [x] 面板内小节分层：「修改密码」「高级设置」原来是 `mt-lg` + 普通字段混在同一个 `gap-md` 流里，读不出层级。改为 `grid gap-sm border-t border-line-decor pt-md`——细线上下各 16px、标题与控件仍 8px 绑定；先试 `border-border`(#f0eee6) 与 `border-border-warm`(#e8e6dc)，在 `--color-bg`(#f5f4ed) 上实测都不可见，取 `.login-card` 已在用的 `--color-line-decor`(#c8c6be / 暗色 #514a41)。规则与实验台 Field 面板的同一份样例写入 DESIGN.md。
- [x] 组件库 App rail 漂移修复（根因两处）：rail 的盒子与内容作用域原本混在 `@utility app-sidebar` 里，而它带着 sticky 与 `100vh`，demo 框承载不了，只能另写一套近似 markup。先拆出 `sidebar-rail`（内容作用域）并把结构提成 `shared/ui/sidebar.tsx` 的 `SidebarFrame`；随后再拆出 `sidebar-frame`（盒子：宽度、边框、表面、折叠宽度），`app-sidebar` 只剩页面锚定，实验台与产品渲染同一个盒子（见阶段七）。
- [x] `SidebarToggle` 两枚箭头的可见性原来由 `.app-sidebar` 作用域里的后代规则决定，实验台单独渲染按钮时两支箭头都不显示；同时它和 `.sidebar-toggle [data-toggle-icon] { opacity: 0 }` 在同层内特异度打平，顺序不可依赖。状态改挂到按钮自己的 `data-collapsed`，规则写在 `@utility sidebar-toggle` 内部并提高特异度；实测展开 `collapse=1/expand=0`、折叠 `collapse=0/expand=1`。
- [x] Report surfaces 面板去掉"卡中卡"：报告页是 `bg-bg` 上的文档表面，实验台原来把它塞进 `layout="card"` 的 `bg-surface` 里，读起来像一张卡套一张卡。这一步只解决了皮肤，纸面仍被卡片的内边距挤窄到放不下三列评分——真正的收口在阶段七（渲染真实组件并给产品同宽）。

### 阶段七：几何漂移根因收口与实验台边界（视觉验收驱动）

- [x] **折叠 rail 的 2px 偏心（根因链，非本次引入）**：`--layout-sidebar-collapsed-inline-size: 51px` 是手调字面量，容不下 rail 自己声明的行几何。实测产品折叠态行盒 34px、图标左 9px / 右 5px（实验台 33px / 左 9 / 右 4），折叠按钮 36px 也溢出 2px。链条：`dac2f99` 写下推导式 → `b114707` 语义化时拍平成 51px（行内 padding 仍推导）→ `ff88b12` 用 `design_lock_values` 把 51px 锁死 → `6957e87` 迁移时把 5 条布局锁静默删掉 → `2f4c2b4` 控件合并到 36px 而容器不动，缺口诞生。修法两条：token 改为 `calc(控件 + gutter × 2 + 边框)` = 53px；`--sidebar-btn-padding-inline` 补回按钮自身用于画焦点环的 1px 边框，即 `(控件 − glyph − 边框 × 2) / 2` = 7px。实测折叠行盒 36×36、图标左右各 8px。
- [x] **rail 三层拆分**：`sidebar-frame`（盒子：宽度、边框、表面、折叠宽度与过渡）+ `app-sidebar`（sticky、层级、`100vh`，并把 frame 撑满）+ `sidebar-rail`（内容作用域）。`SidebarFrame` 组件拥有 `sidebar-frame sidebar-rail`，产品是 `app-sidebar > SidebarFrame`，实验台让同一个组件直接落在 `bg-bg` 上——实验台不再用自己的 `border` 偷走 2px 宽度（此前 rail 实测 49px vs 产品 50px）。
- [x] **`@visual` 折叠 rail 几何闭合断言**：容器宽度、行盒宽高、折叠按钮、图标左右间隙全部与 token 实测对齐，测试里不写尺寸字面量。红测验证：把 token 改回 `51px` 该用例失败（Expected 53 / Received 51）。顺带修掉两处随档位合并而失效的旧断言（登录按钮 `>= 34` 改为等于 `--ui-height-control`），并把 rail 动画 settle 改为 `getAnimations({ subtree: true })`——宽度过渡已从 aside 移到 frame，旧的 `sidebar.getAnimations()` 会静默不等。
- [x] **实验台迁到 `app/lab`**：`shared/**` 禁止 import `features/**`，所以报告与简历的真实组件此前根本进不了实验台，只能手写近似版（这正是 Report surfaces 漂移的机制性原因）。实验台是 DEV-only 路由挂载的开发者界面，属组合根，迁到 `app/lab/ComponentLab.tsx` 后即可渲染 `@/features/report` 的 `StructuredReport`/`ScoreCard`/`ReportCarouselNavigation`/`Trait`/`ReviewDetail` 与 `@/features/resume` 的 `ResumeRow`。`verify:architecture` 与 `verify:production` 均通过（产物仍不含 `/components-lab`）。
- [x] **报告与简历真实化**：Report 面板渲染整页 `StructuredReport`，Report blocks 渲染评分卡、轮播导航、空态 `Trait` 对与 `ReviewDetail`；两者按产品内容宽度 `--layout-workspace-content-max-inline-size` 呈现，不套卡片皮肤——`report-columns` 是 `auto-fit minmax(200px, 1fr)`，卡片内边距会把纸面挤到 620px，第三个评分板块因此换行。实测纸面 800px、三列各 228px 同排、总体分右对齐。简历行从 `ResumeManagementPanel` 抽成 `features/resume/ResumeRow.tsx` 并由 barrel 导出，实验台与设置弹窗共用。
- [x] **样例脱敏口径**：实验台的数据值一律按角色命名（`示例简历.pdf`、`当前会话`、`岗位条目一`、`评分理由示例文本`、`选项一/二`、`模型名称`），与 Button 面板的「主要操作 / 次要操作」同一口径；控件文案（导航项、动作、字段标签）保留真实产品文案，因为它们本身就是被检阅的对象。样例集中在 `app/lab/samples.ts`。
- [x] **`verify:tokens` 解析层重写（为什么门禁看不见 51px）**：旧扫描按行读，且 `@utility` 内的自定义属性一律放行、值里出现 `var(` 即整条放行、prettier 折行的 `calc()` 扫不到、`:root.dark,` 这类跨行选择器不被识别为 token 块。现在按声明读（跨行、含自定义属性、`var(--x, 0px)` 回退不算尺寸），并新增 `derived_tokens` 登记表（5 个容器 token 必须继续引用其来源）与「盒尺寸不得整值借用与某档 glyph 等值的 spacing 步骤」。红测验证三类植入缺陷全部被抓；读取器另有声明数下限自检，防止解析器空转造成假绿。
- [x] 同类字面量顺手收口：`--sidebar-icon-glyph-size: 20px` 与 `--ui-glyph-md` 重复声明，删除并直接引用后者；按钮 spinner 与 toast 关闭盒的 16/24 从 `--spacing-*` 改指 `--ui-glyph-*`；纸质纹理的 `320px 320px` 收进 `--bg-paper-tile-size`；`sidebar-sessions` 手工复刻的 `calc(260 - 16)` 宽度（漏算边框、重复扣 padding，实测溢出 1px）改为 `sidebar-pane` 钉住 `inset-inline: 0` 由槽位决定。
- [x] 会话空态与分组标题同层级：`SessionGroup` 的 `emptyLabel` 原是 `ms-xs text-xs text-text-tertiary`，读起来比"已归档"低一级；改为与分组标题同一套 `mx-sm text-xs font-semibold tracking-label text-text-tertiary`。

### 阶段七b：间距与分割线全量清点（实测驱动）

- [x] 清点方法：用一次性 Playwright 探针遍历 8 个界面（登录、工作台、设置五个分区、看板、实验台）里每一条 1px 实线边，记录线色、背后表面、线上下两侧的实测间隙，共 167 条。
- [x] **色号离群只有一处**：全应用的分割线、卡片边、面板头/尾、报告分段、列表行都用 `--color-border`；唯一例外是上一轮我为设置小节引入的 3 个 `border-line-decor` 调用点（实测线背 `--color-bg` 时 `--color-border` 对比度仅 1.03，才被迫加深）。按「以侧边栏为准」统一回 `--color-border`，同时把设置弹窗内容列从 `bg-bg` 回到 Dialog 壳层自身的 `--color-surface`——否则统一后的线在该底色上不可见。实测两处小节线的背衬与侧栏同为 `rgb(250,249,245)`，`bg-surface` 在设置侧栏上随之成为冗余声明，一并删除。
- [x] **侧栏「进行中」贴线**：主操作下的分割线实测上 27px / 下 0px（线的下线没人给）。改为 `SidebarFrame` 中间容器 `gap-md`，实测变成 27 / 16；同时删除实验台 nav 上补偿性的 `pt-sm`——产品没有它，同一个组件因此有两种渲染，属上一轮 rail 拆分的残留。
- [x] 加载中提示与分组标题同层级（`AppShell` 的「正在加载会话」原 `ms-xs text-xs`，与刚统一的空态写法不一致）。
- [x] 新增 `@visual` 门禁：遍历分割线角色元素（恰好一侧 1px、无圆角、背景透明），断线两侧间隙均 ≥ `--spacing-sm`。红测：去掉 `gap-md` 后报 `div.border-b.border-border.pb-md — above 27px, below 0px`。
- [x] 死 token 与死注释清理：`--color-line-decor-light` 全仓零消费者（`verify:tokens` 的"声明未消费"检查因 `@theme` 桥接行而漏判）；`index.css` 里两条指向已删除分区的空注释（Composer/Settings dropdown）。
- [x] 清点中判为**非缺陷**、避免后人重复追查的项：`list-row`/`option-card` 是整圈 1px 盒边（卡片不是分割线）；`row-label-end` 在 `auto-fit` 网格里没有同列后继，探针的"下一条"会跨列误报负值；`workspace-header` 下线 49px 是页面内容自己的内边距；`.brand-metaballs` 的 1px 是 `color-mix` 装饰环；焦点态把控件边框染成 `--color-focus-*` 不是分割线。

### 阶段八：结构收尾与判据铺满（对齐决策后执行）

- [x] **`features/report` 按职责拆分**：`index.tsx` 533 行同时是 barrel 与全部视图，是七个 feature 里唯一的异类。拆为 `parse.ts`（`parseInterviewReport` 与结构校验，133 行）、`report-view.tsx`（`ReportPanel`/`StructuredReport`，81 行）、`report-sections.tsx`（评分卡、阶段轮播、逐题复盘、训练计划、`Trait`/`Signal`/`ReviewDetail`，305 行）、`print.ts`（打印与 `is-printing-report`）、`index.ts` 纯再导出。barrel 只导出确有外部消费者的符号，`parseInterviewReport` 保持 feature 内私有（畸形报告降级由 `@smoke` 走界面验证）。
- [x] **`features/insight` → `features/analytics`**：目录名是术语链上最后一处异类（组件 `AnalyticsPage`、类型 `Analytics*`、路由 `/analytics`、接口 `/api/analytics/*` 早已统一）。同时补 `features/analytics/index.ts`，`main.tsx` 不再深导入 `@/features/insight/AnalyticsPage`。后端 `InsightQueryService` 有意不改——它是洞察域服务名，与前端展示路由不必同名，改动会牵动论文证据链。
- [x] **语音实时模式视觉资产**：`tests/demo-harness.ts` 新增 `installVoiceLane`，只假 `/api/ws` 传输与 `window.Audio` 播放端，跑真实 `useVoiceInterview` 状态机与真实 composer。`capture:surfaces` 的 1 张回退帧扩为 5 张：connected（`语音模式已连接`）、listening（按住说话，`正在聆听` + 波形）、processing（`正在处理`）、speaking（`面试官正在回答`）、fallback（error 帧回退到文字模式）。资产集 27 → 31 张（阶段十二把聆听帧拆成按住/转录两帧，现为 6 帧 32 张）。这些帧证明客户端状态与界面，**不**证明上游语音质量；后者仍需一次真实上游通话，无法在 CI 重生成。副作用：harness 不再产生 `/api/ws` 的 `ECONNREFUSED` 噪声。
- [x] **实验台像素判据按面板铺满**：`components-lab-{light,dark}-win32` 两张视口截图（只覆盖首屏，前两轮 rail/report/resume 的改动全在首屏以下、一次没报）换成 16 个面板 × 亮暗 = 32 张 locator 基线（该面板数在阶段十一b 收敛为 14 个 / 28 张）。测试内维护面板清单并断言 `.workspace-page__content > section` 的首个 `h2` 序列与清单相等——新增面板未登记先失败在覆盖断言上。含 WebGL 品牌球的两个面板（App rail、Brand）mask 掉品牌球，另以"正方形 + 全圆角"几何断言把关（沿用 404 的处置）。红测：给 Session list 面板加一个 `pt-md`，只有 `component-lab-session-list-light.png` 报 diff。
- [x] **`capture:surfaces` manifest 可追溯**：除 `revision` 外记录 `inputsMatchRevision` 与 `dirtyInputFiles`（排除截图集自身），工作树与提交不一致时 `console.warn`。实测采集时输出 `ran on a dirty tree: 16 input file(s) differ from 2170c90`。

### 阶段九：实验台二次排查（遗漏项 / 重复项 / 非样例件）

- [x] **判据漏洞：面板基线只绘制首屏**。元素截图只能画出滚动容器揭示的像素，实验台面板在 `.workspace-page__content.scrollable`（`clientHeight` 仅视口高）内，于是高于视口的面板写出「尺寸对、下半张空白」的基线——`component-lab-report-light` 的 1935px 里只有约 650px 有内容，整页报告从未真正进入像素判据。测试改为先量面板高度与容器可视高的差额、按差额扩窗，再断言面板完整落在容器内才取图。红测依据：修复前后 `component-lab-report-*` 与 `component-lab-prompt-bar-*` 的像素差（详见 `docs/quality/ui-quality-system.md`）。
- [x] **字段尾部操作位收口**：登录页密码、设置「修改密码」、API Key、实验台四处各写了一份 `field-actions-*` + `absolute inset-y-0 inset-e-(--ui-control-inset)` + 裸 `button.field-action` 的标记。改为 `shared/ui/field.tsx` 的 `FieldActions`（留白按动作数量推导）与 `FieldAction`（tooltip 与 `aria-label` 同源）。顺带修掉真实缺陷：API Key 字段在 `hasApiKey` 为假时只画一个按钮，外层却写死 `field-actions-2`，多留一档留白。
- [x] **回答组合器抽出产品拥有的呈现层** `AnswerComposerSurface`：实验台的准备态/回答态此前手抄 composer 标记并已经漂移（占位文案 `输入回答...` 对产品的 `输入回答…`、缺附件行、缺锁定态上下文、自造的模型事实位）。现在准备态渲染 `InterviewSetupComposer`、回答态与语音两态渲染该呈现层（`voice` 供冻结态、文字态可点切换），`InterviewAnswerComposer` 退为容器 + `useVoiceInterview`。产品侧像素零变化（`interview-prompt-bar`、`interview-model-menu` 基线未改动即通过）。
- [x] **rail 品牌位与结构**：`SidebarBrand` 成为 `AppShell` 与实验台共用拥有者；实验台 rail 的会话分组从面板里的孤立 `DemoGroup` 移进 rail 内部（产品就在这里列会话），展开/折叠两态并排，面板高度 1203 → 819。两态按内容高度呈现，贴底与交叉淡入仍由工作区真实截图覆盖。
- [x] **重复数据**：实验台的 `themeChoices` 是 `ThemePanel` 内 `themeOptions` 的手抄副本，现由 `features/settings` 一处导出、两侧同引；`ReasoningLevel` 与 `REASONING_LABELS` 曾在 `features/interview/types.ts` 与 `features/settings/types.ts` 各定义一份（interview 的标签表实际无人消费），删去副本、interview 改从 settings 引用。
- [x] **非样例件与假文案清理**：删除产品不存在的「通知」Bell 按钮（Button 的 Box 组与 Tooltip 组曾各摆一个同款）；DropdownMenu 面板的「新建会话/排序方式/已归档/JD 匹配」是产品没有的菜单，条目改为角色命名（菜单项、单选项一、多选项、禁用项…）；Toast/Confirm 的「已保存到工作台」「模型额度偏低」等伪业务文案改为角色命名；Field 小节的思考深度选项改用 `REASONING_LABELS`；`sort` 状态曾被 Field 小节与下拉菜单共用（改一处会移动另一处），拆为 `reasoning` 与 `sort`；Empty & Error 的重试按钮换用 `RefreshCw`。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`（209 declarations）、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（9 例）、`test:smoke`（35 例）、`verify:byok`（4）、`verify:dark`（2）、`verify:a11y`（1）全通过；实验台 30 张基线按新判据重生成并逐张复核。
- [x] **报告模块回到角色与容器**：新增 `type-document-title`（serif `xl` / semibold / `display`）承担文档面 `h1`——与 `type-metric` 同字号同字高、只差权重，不破坏「同一字号一种行高」；四处 `h3` 统一为 `type-subtitle`（此前 `leading-heading` 与 `leading-base` 并存，权重还随 body 落到 regular）。六个 `border-t py-lg` 段落改 `grid gap-lg` 并删 6 处 `mb-lg`；阶段卡与逐题卡按「标题+摘要 `gap-md` / 明细 `gap-lg`」分组；`Signal`/`Trait`/`TrainingPlan` 的 `mt-sm`/`mb-sm` 全部交给容器 `gap-sm`；hero 由 `gap-xs`+`mt-sm` 改为「eyebrow+标题」内块 + 外块 `gap-sm`，`pb-xl`(32) 回 24 档。
- [x] **分数与卡片间距**：`ScoreTile` 数值改用 `type-metric`（与看板同一角色）、标签改用 `type-label`，`my-sm` 由 tile 的 `gap-sm` 接管；`GeneratingCard` 两处 `mb-lg` 交给 `.generating-card` 的 `gap-lg`，标题与提示自成一档 `gap-xs`，`generating-title` 不再自带 margin；`SessionGroup` 改 `display: grid; gap: --spacing-sm`，分组标题的 `mb-sm` 删除（实测标题→列表仍 8px、行距 44px）；`Pin` 图标从 `size={12}` 回到 `--ui-glyph-sm`。
- [x] **空态落点与细线配对**：`MessageThread` 就绪提示与 `InterviewSession` 加载态改走 `empty-state`（此前各自手写 flex 居中 + tertiary），错误态去掉多余外层 wrapper；`AnalyticsPage` 薄弱点条目去掉 `border-border`，与 `ScoreTile` 同为「无框 muted 面片」，不再在卡片里套带边框卡片。
- [x] **token 门禁修正**：消费者计数原先把 `@theme` 的自我镜像 `--x: var(--x)` 当成引用，也完全不认 Tailwind 由命名空间生成的类名。两处同时修好后 `--color-sand`、`--color-text-button`、`--color-brand-light` 被判死并删除（209 → 206 条），而 `--spacing-0`（由 `m-0` 消费）一类活 token 不再误报。shadcn 语义桥按决策保留，在 `ui-tokens.json` 与脚本注释里写明「当前无一方消费者、作为外部组件适配层有意豁免」。另清掉 `@layer base` 里被未分层 `body { font }` 永久压过的 `body { font-family }`，以及 `prompt-bar` 在 JS 里重复的 100px 上限（`max-block-size` 已拥有它）。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`（206 declarations）、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（9 例）、`test:smoke`（35 例）、`verify:byok`/`dark`/`a11y`、`capture:surfaces` 全通过；实验台 28 张基线重生成并复核。一处 smoke 选择器随 hero 结构更新（`report-hero > p:first-child` → `report-hero p`），断言内容不变。

### 阶段十二：语音模式改为「按钮内电平 + 文字框转录」

- [x] **删除输入区里的大块占位**：`VoiceIndicator`（状态点 + 文案 + 9 条 CSS 假波形整块替换输入区）连同 `voiceStatusLabel` 一起删除，`PromptBar` 的 `inputContent` 入参随唯一消费者消失，`--animate-status-pulse`/`--animate-voice-level` 两条动画 token 与 keyframes 一并清掉（206 → 205 declarations）。四条状态文案（语音模式已连接 / 正在聆听 / 正在处理 / 面试官正在回答）不再存在——它们把一条输入通道说成了一块仪表。
- [x] **转录改为草稿**：`useVoiceInterview` 的 `user_text` 不再直接生成一轮 user 消息，改由 `onTranscript` 交给容器写入草稿（已有内容时按行追加）；文字框在两种模式下常驻可编辑。新增 `@smoke` 用例锁住这条契约：转录进 `面试回答` 输入框、气泡区不出现该文本，点「发送」后才成为一轮。
- [x] **电平进按钮**：`VoiceLevelMeter`（`shared/ui/prompt-bar.tsx`）自建 `AudioContext` + `AnalyserNode`，rAF 里算 RMS 并只写一个 `--voice-level` 自定义属性，因此实时电平不产生 React 重渲染；5 根条各自乘系数，读作波形而不是整块起伏。按钮 `position: relative` + 标签淡出，宽度由标签继续撑住（capture 实测按下前后宽度一致，写成断言）。处理中复用 `Button` 的 loading，回放期间禁用按下。reduced-motion 下只采样一帧即停，保留读数去掉泵动——这是截图资产里能看到波形的原因，也是 `prefers-reduced-motion` 的正确读法。
- [x] 语音链路的桩补齐：`installVoiceLane` 现在同时假 `getUserMedia`、`MediaRecorder` 与 `AudioContext`（分析器返回固定正弦，逐次采集同一波形），所以 6 张语音帧可复现；帧义改为 connected / listening / transcript / processing / speaking / fallback。这些帧只证明客户端状态与渲染，不证明上游语音质量。
- [x] 实验台语音分组从「聆听 / 播报」改为「按住 / 处理中」——`speaking` 已无独立可视形态，画出来只会像坏掉的按钮；实验台的电平表无麦克风可读，停在地板高度。
- [x] 一处 capture 定位踩坑记录：按住时按钮的可及名变成 `松开发送`，`getByRole('button', { name: '按住说话' })` 会失效，改按 `.prelude-button--hold` 定位。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`（205 declarations）、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（9 例）、`test:smoke`（36 例，含新增的转录草稿用例）、`verify:byok`/`dark`/`a11y`、`capture:surfaces`（6 张语音帧）全通过；`09-composer-voice-listening` 与实验台 Prompt Bar 基线逐张确认电平波形、按钮宽度与发送位。

### 阶段十二b：发送统一为图标，并修掉按住态的 2px 错位

- [x] **尾部操作区两种模式同构**：`发送` 在文字模式也改成 `ArrowUp` 图标按钮（`size="icon"` + `IconTooltip` + `aria-label="发送"`），空草稿时带禁用态常驻，操作区不再因输入而重排；`rightActions` 由「语音/文字两套」合并为一套三元表达式，两种模式的控件顺序与几何完全一致。`shape="action"` 仍由 `开始面试` 使用，未变成死变体。
- [x] **三按钮不同线的根因**：`.prelude-button--hold[data-pressed] { transform: translateY(2px) }`——为瞬间点击设计的按压下沉，用在要按住数秒的控件上就成了持续错位。实测（实验台语音态）同行按钮 top 2837.72 对 2839.72。删除该位移，反馈改由环影 `--shadow-ring-deep` + 标签淡出 + 电平覆盖承担；这条规则对一次性按钮不受影响（选择器只命中 hold 变体）。
- [x] 新增第 10 条像素判据：`@visual keeps every composer control on one centre line` 遍历实验台每个 `[data-slot="prompt-bar-controls"]` 尾部簇，断言簇内所有按钮共享同一 top/bottom（≥2 个控件才检查），违规时回传按钮名与两侧坐标。红测：把 `translateY(2px)` 加回去即失败并打印 `tops`。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`(205)、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（**10 例**）、`test:smoke`（36 例）、`byok`/`dark`/`a11y`、`capture:surfaces` 全通过；实验台 Prompt Bar 亮暗基线与 `09-composer-voice-*` 逐张复核。

### 阶段十一：实验台文案分寸与说明文字（两次纠偏后定稿）

- [x] **先承认两次走偏**：上一轮我把「实验台必须渲染产品组件」推成「实验台必须显示产品文案」，并在 `DESIGN.md` 写下「控件文案保留真实产品文案」——这条是我自己的解释，不是需求。用户指出后我反向做成「一切可见文字都脱敏」，开始把 composer/菜单的固有词拆成 `copy` 入参，这又是另一个极端；该半程已回退（`composer-copy.ts` 删除、`voiceStatusLabel` 复原）。
- [x] **定稿的分寸**（已写入 `DESIGN.md`）：调用方传文案的槽位一律角色化；组件焊死的词照原样显示，因为那些词就是被检阅的控件本身。报告作为文档样张走 `reportCopy`（缺省即产品词条，产品侧零改动）。
- [x] 槽位角色化落地：rail 行与会话分组（`主要操作`/`分组一`/`条目一`/`空态文案`/`分区二`）、设置导航五段（`分区一…五`，仍复用 `sections` 的条目与图标）、主题卡（`选项N`/`说明N`，仍复用 `themeOptions` 的三项与预览）、分段控件（两项/三组真实条目改为 `选项一…三`，撤回上一轮的「真实条目」口径）、Field 七个字段与占位与说明、消息气泡说话人与正文、生成态卡片、空态与失败态、浮层与确认框全部文案、Prompt Bar 的模型事实位与附件芯片改用 fixture。
- [x] 说明文字：14 个面板 `description` 全部缩到归属路径（`shared/ui/panel`、`features/interview · shared/ui/prompt-bar`…），报告节标题下的长句同样删除；契约只在 `DESIGN.md` 说一次。
- [x] 样张正文：`用于检查气泡最大宽度` 一类自说明句改为 `示例文本一，长度用来检查…` 的中性写法，长短差异保留（那才是检查换行的手段）；fixture 文件名/条目名统一为 `示例文件一.pdf`/`示例条目一`，`不可用模型` 改为 `示例模型三` 并注明它表达的是 `reasoning: false`；模型名两处不一致（`当前模型 · 默认` 与 `示例模型`）收敛到 `sampleModelName` 一处。
- [x] 报告文案入参：新增 `features/report/copy.ts`（`ReportCopy` + `reportCopy` 缺省），`StructuredReport` 接可选 `copy`，子组件读同一对象；产品调用点（`ReportPanel`）不传，像素与文案完全不变。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`（206）、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（9 例）、`test:smoke`（35 例）、`verify:byok`/`dark`/`a11y`、`capture:surfaces` 全通过；实验台 28 张基线重生成并复核（App rail、Report 已逐张确认）。capture 与 surfaces 的两处选择器随实验台文案更新（`打开工作台浮层`、`危险确认`），断言内容不变。
- [x] **同轮复核再清三处（用户指出）**：① Panel 面板的正文把面板 `description` 已声明的契约又说了一遍（「标题行不随滚动移动，内容区自带内边距并独立滚动」），正文改为不含信息的示例文案，契约只在描述里出现一次。② SegmentedControl 面板的「会话/报告/看板」是产品里不存在的第三套条目，改为产品那两组真实条目各一份（登录/注册、面试/报告）。③ Report blocks 整块是 Report 整页同一段的重复渲染（`ScoreCard`、轮播导航、`Trait`、`ReviewDetail` 都在整页里），删除该面板与其两张基线；`Trait` 的空态改由 `sampleReport.weaknesses: []` 在整页里呈现，覆盖不丢。`features/report/index.ts` 随之收回到确有外部消费者的符号（`ReportCarouselNavigation`/`ReviewDetail`/`ScoreCard`/`Trait` 已无外部调用点）。实验台 15 → 14 个面板、28 张基线，上述检查与 `capture:surfaces` 重跑全通过。

### 阶段十：统一排查样式、排版、间距、对齐、layout 与 token 收敛

- [x] **修掉本轮自己引入的回归（最高优先）**：`FieldActions` 用模板字符串拼类名 `` `field-actions-${actions.length}` ``，而 Tailwind 只从源码文本读类名——全仓唯一的功能 utility `@utility field-actions-*` 因此**一条规则都不产出**。实测后果：包裹层 `position: static`（绝对定位的操作位失去锚点）、`--field-trailing-gutter` 未定义回退 0、输入框尾部留白从 50px 退回 16px，而按钮盒是 32px——文字被压住 34px，按钮顶边比输入框高 9px。登录、设置「修改密码」、API Key、实验台四处同时中招，且 `verify:ui` 的「无消费者」检查把动态前缀当消费者放过、像素基线因字段为空而看不出来。改为字面量分支 + `[ReactNode] | [ReactNode, ReactNode]` 元组收窄数量。
- [x] 同一失效模式的三条门禁：`verify:ui` 要求每个 `@utility name-*` 存在字面量调用点（红测：改回动态拼接即 `FAIL (1)`）；`@visual` 在登录页实测「操作位落在控件盒内 + 垂直居中 + 输入框尾部留白 ≥ 按钮宽」（红测：失败在 `actionInsideField: false`）；构建产物复核两条规则确实产出。
- [x] 实验台 Field 的「小节」容器 `gap-md` → `gap-sm`，回到 `DESIGN.md` 与两处产品调用点同一形态——样例本身此前教的是错的写法。
- [x] **排查出、本轮未改的留存项**（按严重度见会话报告）：报告模块整体绕过 `type-*` 角色并让 `mt-*` 承担绑定间距；`MessageThread`/`InterviewSession` 的加载与空态没走 `empty-state`；分数数字存在三种写法；`AnalyticsPage` 用 `border-border` 配 `bg-surface-muted` 违反细线标准；4 个 token（`--color-brand-light`/`--radius-2xl`/`--spacing-0`/`--font-mono`）与桥接别名 `--ease-standard` 因 `@theme` 镜像行被门禁误判为有消费者；`session-row` 的 `size={12}` 与 `prompt-bar` JS 里的 `100` 各绕开一次 token；`@layer base` 里的 `body { font-family }` 被未分层的 `body { font }` 永久压过。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（9 例）、`test:smoke`（35 例）、`verify:byok`/`dark`/`a11y` 全通过；实验台 Field 亮暗基线与 `capture:surfaces` 重跑并复核。

### 阶段十三：全仓复核与基础设施、门禁、契约补齐（逐项核对后执行）

- [x] **先纠正上一轮会话自己报错的 6 条**：`elevated-*` 与 `--shadow-*` 不是双轨（`index.css:1473` 注明 Tailwind 的 `shadow-*` 命名空间被 UI lint 划给调色板，token 阴影必须经 elevation 类到达）；`PinInterviewSession` 只有一次仓储写，不需要事务；`ModelProfileService` 用 `TransactionTemplate`（`:47`）做程序式事务；`InterviewJudgeService` 的 10×500ms 是 Redis 锁获取循环而非请求重试；`StreamChatTurn:93` 的 catch 有注释且 `finally` 仍 `complete()`；`--composer-height: 260px` 实测未漂移（composer 真实高 162px、6 行草稿 202px，末条消息到 composer 顶边留 73.77 / 33.77px）——它是过度预留 58–98px 且无推导无断言，属未治理而非缺陷。
- [x] **全新克隆的一键 Docker 启动是断的**：`docker-compose.yml:97` 要求 `APP_CRYPTO_AES_SECRET` 非空，而 `.env.example:8` 交付空值，`start-docker.bat:12` 又正是把它复制成 `.env`。实跑证实：`APP_CRYPTO_AES_SECRET= docker compose --profile app config` 退出 1。改为交付与 `application-dev.yml:11` 同一把 dev-only 密钥，compose 保留 `:?`（生产覆写缺失仍然响亮地失败），并补上 compose 会读却未登记的 `SPRING_PROFILES_ACTIVE`。
- [x] **生产上传被 nginx 卡死**：`frontend/nginx.conf` 未设 `client_max_body_size`（默认 1MB），后端 `application.yml:16-17` 允许 10MB → >1MB 简历在唯一生产路径上 413。补 `client_max_body_size 10m`；顺带删除 `/actuator/` 反代——`start-docker.bat:6` 的探活直连 `:8080`，这段只是把 health/info/prometheus 暴露到公网 origin。
- [x] **后端 HTTP 契约补齐 34 例**：`InterviewController`（9 端点，产品主链路，此前 `src/test` 全文零引用）、`ResumeController` 5、`AnalyticsController` 5、`AttachmentController` 4、`JobController` 3。两条契约是读代码时才发现的：`POST /chat` 的空 `content` **不能**在 DTO 上加 `@NotBlank`——autoStart 就是靠空正文开场，拒绝发生在 `RunInterviewTurn:45-50`；跨账号任务查询回 404 而非 403，避免用 jobId 探测账号存在。三条植入缺陷（authSessionId 恒 null ×2、`inUse` 恒 false）红测确认全部被抓。
- [x] **token 普查的第二处同类漏判**：`verify-ui-tokens.cjs:323` 用 `[a-z]+-<key>` 匹配类名，`--radius-2xl` 被 `text-2xl` 命中而白拿"有消费者"——与阶段九修掉的 `--color-sand` 同一失效模式。改为单值命名空间钉住前缀（`radius-`→`rounded` 等），门禁随即只报这一条，删除该 token（205 → 204 declarations）。
- [x] **feature 入口违反自己文档写的规则**：`architecture.md:14` 明文"每个 feature 的入口是纯 `index.ts` barrel"，实际 8 个里 5 个是 API 客户端（`settings/index.ts` 写了 `fetchLlmConfig`/`saveProfile`/`uploadAvatar` 等 8 个请求函数，`interview`/`position`/`resume`/`assets` 同类），且 `verify-architecture.cjs` 完全没有这条检查。请求函数抽到各自 `api.ts`、`groupSessions` 归 `session-groups.ts`，barrel 只留确有外部消费者的名字（`AuthStatus`、`finishInterview`、`InterviewContextFacts`、`CreatePositionPayload`、`ResumeUploadResponse` 等 12 个死导出随之删除）；同 feature 的兄弟组件改走相对路径，不再 `import '../index'` 把自己绕回来。新增两条 AST 检查（入口纯度 + 外部消费者），`await import()` 的成员访问计入消费者，4 条新单测覆盖。
- [x] **派生 token 只登记能证明的那一个**：`--layout-select-list-max-block-size: 360px` = 10 × `--ui-height-control`（滚动容器无自身内边距与边框、行高即控件高、无 gap），改为 `calc()` 后像素零变化。其余候选经核对是**巧合等值而非包含关系**：`--layout-brand-mark-inline-size: 72` 与生成态玫瑰同值但无来源关系、`--layout-position-catalog-min-inline-size: 176` 与条目 160 差一个 `--spacing-md` 但目录内边距并非 16、`--layout-sidebar-header-block-size: 60` 是选定的带高。不为其造推导。
- [x] **实验台回到"渲染产品真正渲染的东西"（三组）**：设置导航列此前是实验台自写近似版（`grid gap-sm` 裸列表，没有右细线、没有 `mt-auto`、没有 aside）→ 提为 `shared/ui/navigation.tsx` 的 `SettingsNavigation`，产品与实验台同一拥有者，实验台基线按新判据重生成并逐张确认；主题选项组两处各写一遍 `grid grid-cols-3 gap-sm` + `radiogroup` → 提为 `ThemeChoiceGroup`；生成态居中背景产品写 `flex-1`、实验台写 `w-full` → 提为 `GeneratingSurface`。后两组像素零变化。
- [x] **报告小节标题回到一个拥有者**：`SectionHeading`（eyebrow + `type-title text-balance`，带操作时同行、不带时自成一格）替换 7 处手写；hero 的 `type-document-title` 是另一角色，不并入。像素零变化。
- [x] **补 5 条零断言交互**：主题选择与 `保存主题`（含 `html.dark` 即时生效）、密码显隐（按字段定位到 `field-actions` 内的按钮，此前两个同名字段导致严格模式冲突）、头像上传、简历删除确认、逐题复盘轮播。为此给 demo harness 补 `/api/user/avatar` 路由与一条 `sessionCount: 0` 的简历——删除只对未使用简历开放，此前 fixture 里根本没有可删的行。
- [x] **文档与代码矛盾的批量纠正**：`DESIGN.md` 删掉 CSS 里 0 命中的 `--color-brand-light` 与不产出任何规则的 `text-meta` 原子；`architecture.md` 删 `VoiceIndicator`、`@NamedInterface` 实到 9 个而文档写"八个"（漏 `identity::accounts`）；质量体系"三条实测断言"补成四条；`setup.md` 语音"五帧"改六帧并列出转录帧；`.gitignore:41-43` 注释声称 CI 设 `PLAYWRIGHT_BROWSERS_PATH`，实际只设 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD`；删 `capture:visual`（脚本内容与 `verify:visual` 完全相同却叫 capture，无任何调用点）；`.gitignore` 去掉重复的 `backend/uploads/`。
- [x] **`setup.md` 关于本地跑单测的说法是错的**：原文称开关不设"`mvn clean test` 仍会成功"，但 `PreludeApplicationTest` 无环境开关、会加载完整上下文，Spring Session 在装配阶段就要连 Redis。改为：条件类跳过，但 compose 那组服务仍必须在跑。
- [x] **`handoff.md` 自身的过期陈述**：开头宣称"远端 CI 100% 绿灯"，实际 `origin/arch/optimization-core-path-tests` 停在 `4585bfd`，其后 **16 个提交从未进入 GitHub Actions**——那盏绿灯只覆盖到当时为止。§3/§4 改为明确的历史存档（sentrux 分数、"PositionController 0 接口测试"、239 文件的 diff 数字均已被自己的 §6 推翻），§7 论文风险表"像素基线仅 6 张"改为 32 张并写明未覆盖面。
- [x] **本轮判定为不做/非缺陷**：`elevated-*` 分层（见上）；compose 未传 `DEEPSEEK_API_KEY` 等模型密钥——产品是 BYOK，密钥经 `设置 → 模型管理` 加密入库，服务端环境变量只是可选回退，写进 compose 反而把密钥推到容器编排层；`--radius-xs`/`--radius-3xl`/`--spacing-0` 有单点 CSS 消费者；barrel 无死导出（逐个反查过）。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`(204)、`verify:architecture`（含 7 条单测）、`build` + `verify:cascade`、`verify:production`、`verify:visual`（10 例）、`test:smoke`（**40 例**）、`byok`(4)/`dark`(2)/`a11y`(1)、`capture:surfaces`（32 张）全通过；后端 `mvn test` 255 例（本机未起 Redis，`PreludeApplicationTest` 按上述原因失败，非本轮引入）。实验台 List & Navigation 亮暗基线重生成并逐张确认，`12-settings-2-resumes` 复核。

### 阶段十四：批次 1a–1c 单一拥有者收口（决策后执行）

- [x] **三态归一个拥有者**：22 处散点收进 `shared/ui/empty-state.tsx` 的 `LoadingState`/`EmptyState`/`ErrorState`。加载态此前 6 处三种写法（`aria-live="polite"`、`role="status"`、什么都不标），读屏下是否播报成了运气，现统一 `role="status"`。按定稿口径**失败态一律给重试**，`ProfilePanel`/`ThemePanel`/`LlmSettingsPanel` 三处原本只把错误文本干贴，现各自接回 `refetch()`。顺带修掉 `InterviewSetup` 一处不像其他处的加载态（裸 `text-text-tertiary`，不居中不可达）。
- [x] **内部类名不得外写**：`features/interview` 曾有 14 处直接写 `prelude-menu__*`/`prelude-button__label`。`shared/ui/menu.tsx` 新增 `MenuLabel`（图标/标签/当前值/箭头四件一套），`DropdownMenuItem` 以 `icon` 入参取代 `layout="leading-icon"`，`DropdownMenuRadioItem` 自己拥有截断标签；`shared/ui/button.tsx` 的 `shape="hold"` 新增 `held` 入参。`verify:ui` 现禁止 `shared/ui/**`、`shared/styles/**` 之外出现 `prelude-*__*` 或 `workspace-header__*`。
- [x] **我自己引入并被像素门禁抓住的回归**：把 `--leading` 类从 svg 移到包裹 span 后 svg 丢了 16px 尺寸，`interview-model-menu` 基线立刻报差。改基线是掩盖，正解是把尺寸改为 `.prelude-menu__icon--leading svg` 并让包裹层 `display: contents` 退出布局——svg 回到原来的网格项位置，基线未重生成即通过。
- [x] **八组重复收口**：`PageHeader`（实验台与看板此前手抄 interview 头的四层嵌套并丢了标题 tooltip）、`SubSection`（设置小节带 3 处）、`ReportSection`（报告小节带 8 处，`gap="sm"` 收束建议块）、`SessionGroupLabel`（AppShell 手抄分组题签）、`PromptBarActions`、`HiddenFileInput`（三处隐藏 file input + `sr-only` label，含"选同一个文件两次不触发 change"这个必须清的 value）、`inset-card`/`inset-card-lg`（内嵌卡片此前 5 处三种原子顺序）、`type-lead`（导语宽度此前跟着正文角色手抄 4 处）。
- [x] **一处判定为过度收口并回退**：报告内联分数（2 处、同一文件）曾被抽成 `report-score-note` utility，实测让报告面板高 3px——因为原子的 `text-xs` 自带行高而 utility 只声明了 `font-size`。这正是 `DESIGN.md` 把"报告内联分数"列为该用原子类的例子的原因，故回退。同时把该条口径改写成可判据的形式：一处出现的组合用原子，同一组合重复到第二个拥有者才升角色，并由 `verify:ui` 禁止调用点再手写其原子。
- [x] **七条单一拥有者门禁全部红测**：手写 `className="empty-state"`、`type="file"`、小节带原子、`bg-surface-muted p-*`、分组题签原子、导语宽度、内部类名外写，逐条植入即 `FAIL`。
- [x] **采集帧不可复现这件事被实测确认**：`19-menu-with-submenu` 与 `23-tooltip-icon` 在**同一份代码连续两次** `capture:surfaces` 下 md5 不同（浮层展开与滚动时机），所以它们只能作人工复核，不能当回归判据——与 §7.1 的既有说法一致，本轮补上了实测证据。像素判据仍是 `verify:visual` 的按面板基线。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`(204)、`verify:architecture`(7 单测)、`build` + `verify:cascade`、`verify:production`、`verify:visual`（10 例，**批次 1a–1c 全程零基线重生成**，仅实验台 List & Navigation 因导航列改为真实拥有者按预期重生成并逐张复核）、`test:smoke`（40 例）、`byok`/`dark`/`a11y`、`capture:surfaces` 全通过。
### 阶段十五：批次 1d 几何离群值与门禁射程边界

- [x] **两处逃逸收口**：裸 `z-index: 1`（分段控件按钮压自己的滑块）与 `--login-card-content-layer: 1`（登录卡内容压自己的底色）是同一个概念的两套写法，合并为 `--z-index-local-content` 并入层级阶梯；`.login-card__brand-caption` 的 `line-height: 1.2` 是 `--font-size-xs` 的**第五种**行高（阶梯旁已有注释抱怨过这一点），回到 `--line-height-tight`，与同为引导标签的 `type-eyebrow` 一致。实测该题签单行不换行，所以行高差异不可见——反证那个 1.2 是无作用的漂移。
- [x] **`--composer-height` 的名字在说谎**：实测 composer 高 162px（六行草稿 202px），而该 token 是 260px——它其实是消息流为浮动 composer 预留的**上限**，不是 composer 高度。改名 `--layout-composer-reserve-block-size` 并在 token 处写明它不可推导的理由（composer 高度由内容决定）。`--header-height` 一并并入 `--layout-*`，取消 `DESIGN.md` 原先"某处例外于命名空间"的条款。
- [x] **浮层偏移收进一个拥有者**：`menu.tsx` 6、子菜单 4、`select.tsx` 4、tooltip 8 —— 四个位置器三种裸数字，没有任何东西说明哪种差是故意的。收进 `shared/ui/positioning.ts` 的 `OVERLAY_OFFSET` 并写明理由（tooltip 要避开光标所以比菜单飘得远）。`verify:ui` 现在要求 `sideOffset={…}` 只能取该对象的成员。
- [x] **两条新裸值检查**：`verify:tokens` 的几何扫描此前只管 px/rem/阴影/字重/边框，`z-index` 与无单位 `line-height` 是漏网的同类逃逸。现一并拒绝，`geometry-exempt` 仍可声明例外。红测：植入 `z-index: 3` 与 `line-height: 1.2` 即 `FAIL (2)`；植入 `sideOffset={8}` 即 `FAIL (1)`。
- [x] **明确划在体系之外并写进 `DESIGN.md`**：echarts 的 `grid` 留白（`TREND_GRID` 44/18/30/48）、`lineStyle.width`、雷达 `radius: '64%'`、`RoseThree` 的 `strokeWidth` 是**为图表内容量出来的尺寸**（要装下最宽 y 轴标签与日期标签），不是界面尺度的一档；把它们换算成 `--spacing-*` 只是给无关数字披 token 外衣。它们以具名常量留在图表模块内，字体与颜色照旧经 `cssVarNumber()` 读设计 token。`--layout-chart-block-size` 与 `--layout-select-list-max-block-size` 同为 360px 属巧合，不建立关系。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`(204)、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（10 例，零基线重生成）、`test:smoke`（40 例）、`capture:surfaces` 全通过；三张登录帧因题签行高重采并逐张复核。

### 阶段十六：批次 1e 产品面像素门禁与后端契约在真实依赖下全跑

- [x] **产品面进像素门禁**：新增 6 个产品面 × 亮/暗 = 12 张 `*-win32.png` 基线（登录、注册、面试准备态、看板、产品内报告面、设置主题面板），`verify:visual` 从 10 例 / 32 张 升到 **22 例 / 44 张**。此前产品主链路的视觉回归只由实验台承担，`/login` 与 `/analytics` 改坏了没有任何自动判据会红。
- [x] **我自己引入、被逐字节比对抓住的假判据**：`productSurfaces` 的 `open(page, scheme)` 只有 `login` 真正读了 `scheme`，其余 5 个签名把参数丢了——TypeScript 允许少写参数，所以类型检查、`vp check` 和 22 例全绿都没有异议，而它们的"暗色基线"是亮色帧的逐字节副本，**什么都没判**。是提交前对 12 张新基线做 md5 时看到 5 对完全相同才暴露。修法不是补 5 行 `if`：把 scheme 从各 `open` 上收回来，由循环统一 `preferScheme()` 应用、`expectScheme()` 断言 `html` 上的 `dark` 类真的换了，参数被丢这件事从此不可能发生。红测：修复后先跑一次，5 例如期 `FAIL`（旧基线确实是亮的），再生成并逐张复核暗色帧。同一处 `localStorage.setItem('prelude-theme-preference', …)` 原本在文件里手抄了 3 遍，现只有 `preferScheme` 一份。
- [x] **判据只框可复现的表面**：着色品牌球（登录/注册）与 echarts 画布（看板）在两次相同运行间不逐字节稳定，取图时 mask 掉，动效与配色仍由既有几何断言把关。这是实测结论而非推测——同一份代码连采两次 md5 不同。
- [x] **产品内报告面的桩数据是个假空态**：`installApi` 只提供进行中的会话 7，因此首版基线拍到的永远是"等待报告"卡片。补 `installReportSession()`（完成态 + `summaryReport`），基线才真正拍到报告面；同时给生成中状态补了一条 `@smoke` 断言（等待卡、进度指示、且不得出现 prompt bar 与报告面），`test:smoke` 40 → **41 例**。
- [x] **后端 255 个 `@Test` 首次在真实依赖下 0 跳过**：本地 redis / rabbitmq / mysql / versitygw 起齐后跑全量 `mvn test`，15 个按环境变量启用的条件测试全部执行，`Tests run: 255, Failures: 0, Errors: 0, Skipped: 0`。在此之前这 15 个测试从未在本机以外被验证过。
- [x] 验证：`vp check`、`verify:ui`、`verify:tokens`(204)、`verify:architecture`、`build` + `verify:cascade`、`verify:production`、`verify:visual`（22 例，连跑两次全绿）、`test:smoke`(41)、`byok`(4)/`dark`(2)/`a11y`(1)、后端全量（255/0 skipped）全通过。
- [x] **本机的全绿不是绿**：推送后第一次 Actions run 后端 2m29s 通过，前端红在 `@smoke uploads an avatar`。根因是我写的 `expect(requested('POST', …)).toHaveLength(1)` **同步**读取一个页面仍在追加的数组——`setInputFiles` 在请求真正派发前就返回，本机快所以每次都赢，CI 慢所以每次都输。同一形状另有 3 处（`保存主题`、`保存设置`、确认删除简历）同样在赌时序，一并收进 `sentRequests()`：先 `expect.poll` 等到条数，再读发出去的 body。仍保持同步的是"断言没有请求"的 3 处——poll 一条"没有"会把它变成永真。红绿都用人为延时实测过：把 harness 的 `requests.push` 推迟 400ms，旧写法立刻 `FAIL`、新写法 14 例只剩 1 例 `FAIL`，而那 1 例是延时本身造成的假象（它断言在"行已出现"之后，真实 harness 里 push 早于响应，延时把顺序反转了）。

### 阶段十七：CI 第一次真跑，两次红、两个不同根因

推送后 `4585bfd..` 这一段第一次进入 GitHub Actions。后端 `1m53s` 通过；前端连红两次，**根因互不相同**，都不是产品缺陷。

- [x] **第一次红：判据在赌时序**（见阶段十六末条，`ebadef3` 修复）。`test:smoke` 此后在 CI 通过。
- [x] **第二次红：像素门禁的浏览器是环境属性**。`verify:visual` 报 `component-lab-field-light` 差 20 像素、`-dark` 差 16 像素，其余 20 例全绿。定位过程值得留下：CI 不上传任何失败工件，"N pixels differ" 不给位置，于是先用 Playwright 自带 Chromium 在本地复现——**得到一模一样的 20 / 16**，说明这不是 runner 镜像的随机噪声；再用 `pngjs` 逐像素比对，超阈值的 20（暗色 16）个像素**全部**落在 `(378..396, 324..340)` 这个 9×9 方块里，即 `<textarea>` 右下角由 Blink 自绘的 resize grip（`.prelude-textarea` 声明 `resize: vertical`，`index.css:934`）。根因是 `playwright.config.ts` 的 `channel: 'msedge'`：它把 oracle 的浏览器版本变成"这台机器恰好装了什么"，runner 镜像与工作站不是同一个 Edge，原生控件画法不同，而设计体系根本不拥有那 20 个像素。
- [x] **修根因而不是放宽判据**：删掉 `channel`，改跑 `@playwright/test` 锁定的 Chromium，CI 增加 `npx playwright install chromium`（原先的 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: '1'` 正是"用系统 Edge"这妥协的另一半）。重生成基线后 **44 张里只有 2 张发生变化**——恰好是两张 Field。这既是修复生效的证据，也反过来证明其余 42 张从未依赖浏览器版本：整个基线集里唯一的原生绘制表面就是那个 grip。没有动 `maxDiffPixelRatio`，没有加 mask，没有把基线交给 runner。
- [x] **顺带暴露并修掉一条恒假断言**：`@visual keeps no-data pages lightweight and typographically consistent` 用一次 `emptyState.evaluate(getComputedStyle…)` 读四个样式。换到 Chromium 后四项全空——因为 React 在看板其余请求落地时把这个节点换掉过，句柄指向已脱离文档的元素，而脱离元素的答案是**空样式**，于是"无衬线/有边框/有阴影"全部读成假。改成四条 `toHaveCSS`（每次重试重新解析 locator）。这条在 Edge 上从未红过，属于只在慢浏览器上现形的判据。
- [x] **补上失败证据通道**：`ci.yml` 增加 `upload-artifact`（`if: !cancelled()`），把 `test-results/**/*.png` 与 `error-context.md` 存 7 天。这不算 re-baseline 通道（基线仍只能在开发机重生成），但至少下一次像素失败可以**看图定位**而不是猜。
- [x] 验证：`vp check`、`verify:architecture`、`verify:ui`、`verify:tokens`(204)、`build` + `verify:cascade`、`verify:production`、`verify:visual`（22 例，重生成后连跑两次全绿）、`test:smoke`(41)、`byok`(4)/`dark`(2)/`a11y`(1) 全通过；Chromium 与 Edge 对 42/44 张基线逐字节一致。

### 阶段十八：自审后修判据成色（四条假绿灯）

用户裁决「先修判据成色再谈合并」。三条我自己复核确认、一条被我此前的分析误导过，都记下来。

- [x] **`@dark` 的门禁名不副实**：`expect(colors.every((v) => v.trim().length > 0)).toBe(true)` 只断言自定义属性**非空**，而 `--color-bg` 在浅色块里同样有定义——把 `.dark` 整块删掉它照样绿。改成两半：一条断言**同一页面在两种 scheme 下解析出的调色板不同**（走 `emulateMedia`，存储偏好保持 `system`，避免注册第二个 init script 与前一个打架），一条用两个 `MutationObserver` 断言**`dark` 类先于 `#root` 出现**——后者才是标题里 "before rendering" 承诺的那件事，此前无人判过。两条都做了红测：把 `index.css:365` 的 `:root.dark, .dark` 两个选择器一起改名，调色板例如期 `FAIL`（只改一个仍然绿，因为这个块是双选择器）；把 `initializeTheme()` 推迟到 `setTimeout(…, 50)` 让 React 先挂载，时序例如期 `FAIL`。**第一次探针是无效实验**：我先用 `setTimeout(…, 0)`，而 React 的初次挂载本来就晚于一个宏任务，所以顺序没变、测试照绿——不是断言失灵，是我的反例不成立。
- [x] **`app.behavior.spec.ts` 两处一次性读**：`expect(await modelTrigger.textContent()).toContain(…)` 不重试，而被测标签写在 `InterviewSetup.tsx:53` 的 `setQueryData`，它排在 `:51` 的 `await client.cancelQueries(…)` **之后**——`onMutate` 是 async，取消查询解析后剩余体在微任务续体里跑，点击早已返回。换成 `toHaveText`。这与阶段十六 CI 抓到的那条是同一个类，只是这次由审计而非由红灯发现。
- [x] **`verify:ui` 的死类检查恒为真**：判据是「把所有源码拼成一个大字符串，再 `includes(候选)`」，而候选里含 BEM **块名**（`field__hint` → `field`），于是每个元素类都永远算已消费，整条检查从未报过任何东西。改成要求**完整类名以词边界出现**，并把真正运行时拼接的族显式列出——实测全仓只有 `prelude-button--${variant}` 一处（`shared/ui/button.tsx:32-34`），豁免面从"所有下划线词干"缩到一条正则。红测：塞一个 `.probe-dead-widget` 即 `FAIL (1)`；当前代码库**确实没有死类**，所以这条从"永远绿且无意义"变成"绿且有意义"。
- [x] **内部类泄漏门禁只看得见形状、看不见归属**：正则 `/\b((?:prelude-[a-z-]+|workspace-header)__[a-z-]+)/g` 只匹配带 `__` 的 BEM，而 `features/interview/components/MenuPrimitives.tsx:49` 一直在手写 `prompt-bar-control prompt-bar-control-text ui-action`——整段触发器（类名、截断 span、chevron）把 `shared/ui/prompt-bar.tsx` 已经画过的东西重画了一遍，门禁看不见。**修法不是扩正则**：为组件私有标记注册的 `@utility` 没有形状可认，归属是设计决定，只能声明。于是新增 `/* @internal src/<owner>.tsx */` 标记（`DESIGN.md:107` 那条"一个 utility 只能有一处拥有"的机器可读形式），`verify:ui` 解析标记、按 class 位置扫描、覆盖 `-` 后缀派生；同时把那段手抄触发器收进 `prompt-bar.tsx` 的 `PromptBarModelTrigger`。红测：在 `AnalyticsPage` 的 className 里加 `prompt-bar-control` 即 `FAIL (1)`。
- [x] **这次重构自己引入又被测试抓住的缺陷**：`PromptBarModelTrigger` 最初不收 `...props`，而 Base UI 的 `Menu.Trigger render={…}` 要把 `onClick`/`aria-*` 交给触发元素——组件吞掉 props 后按钮渲染正常但**菜单永不打开**，3 条 smoke 与 1 条 visual 立刻红。改成 `ComponentProps<'button'>` + `cn` 合并 className 后 5 例全绿。教训：把 markup 上提成组件时，作为 trigger 使用的组件必须转发 props，这不是可选项。
- [x] **审计过程中我自己先算错过一次**：第一版归属分析脚本拿整个文件源码做词匹配，于是 `import … from '@/shared/ui/session-row'` 被算成"写出了 `session-row` 这个类"，报出 13 条泄漏。改成只扫 class 位置（`className="…"` / `` className={`…`} `` / `cn(…)`）后，**真实泄漏只剩 `MenuPrimitives.tsx` 那一行**，其余全是导入路径造成的假阳性——包括 `empty-state`、`generating-card`、`option-card` 这些我差点当缺陷去"修"的名字。
- [x] 验证：`vp check`、`verify:architecture`、`verify:ui`、`verify:tokens`(204)、`build` + `verify:cascade`、`verify:production`、`verify:visual`(22)、`test:smoke`(41)、`byok`(4)、`dark`(**3**，新增一条)、`a11y`(1) 全通过。

### 阶段十九：收窄 interview 的跨模块导出面（自审 P0 之一）

- [x] **端口化反而加宽了耦合这件事已修**：`interview.application.port` 原先被 `@NamedInterface("integration")` **整包导出**，于是 `voice/application/VoiceInterviewTurnService` 直接依赖 `InterviewSessionRepository`——一个含 `add`/`update`/`deleteOwned`/`listByUser` 的 CRUD 仓储。voice 真正需要的只有一件事：「这个账号的这个会话还在进行中吗」（`VoiceWebSocketHandler` 拿到返回值后只做判空，存的是自己传进去的 id）。
- [x] **改法**：三个 DB 端口移到不导出的 `interview.application.repository`；`port` 只留真正的跨模块契约；新增导出接口 `InterviewSessionGuard.isOngoing(accountId, sessionId)`，由 `InterviewSessionGuardAdapter` 实现。实测 `InterviewMessageRepository`、`InterviewStageRepository`、`InterviewContextPort` 的**外部消费者为 0**，它们被导出纯属顺手。
- [x] **红测证明这条边界现在真的立得住**：给 voice 重新注入 `InterviewSessionRepository` 后 `ApplicationModulesTest` 报 `Module 'voice' depends on non-exposed type …InterviewSessionRepository within module 'interview'!`。改动前这个依赖完全合法——因为整个包都被卖了。
- [x] **顺手修掉审计点名的另一条假判据**：`anActiveSessionProcessesBusinessFrames` 只有两条 `never()` 断言，名字承诺"处理业务帧"而代码只证明"什么都没发生"。改名为 `anActiveSessionIsAcceptedWithoutClosingTheSocketOrReportingAnError`，并补一条真正的反例（会话存在但已结束 → 必须回一帧错误且不关闭连接），`@Test` 255 → 256。
- [x] **另一条 P0 判定为不可单做**：`artifact`/`interview` 那 7 个 `BaseMapper<领域类>` 缺 `@TableName` 是真风险（`position` 改名时已崩过一次），但**不能就地补注解**——这些类住在 `*/domain/`，而 `FrameworkLeakageTest.DOMAIN_STAYS_FRAMEWORK_FREE` 明令禁止 `..domain..` 依赖 mybatis/ibatis，注释写着"携带 ORM 注解的领域模型已经不是领域模型"。用测试去 pin 表名同样不成立：要么重实现一遍 MyBatis-Plus 的驼峰推导（那是代理判据，不是 MP 的判据），要么让注解和迁移脚本互相 pin 而无人校验。所以这条**必须**并进已批准的 `*Entity` 分离批次一起做，先分离、再补注解、再加"每个 mapper 的泛型参数必须带 `@TableName`"的机检。
- [x] 验证：后端全量 `Tests run: 256, Failures: 0, Errors: 0, Skipped: 0`（四个依赖服务在跑，0 跳过）。

---

## 7. 关键风险与留存问题

1. **视觉像素回归**：`npm run verify:visual` 现有 22 例、44 张 `*-win32.png` 基线——组件检查面按面板逐张 28 张、产品面按面逐张 12 张（登录、注册、面试准备态、看板、产品内报告面、设置主题面板）、Prompt Bar / 模型菜单 / 设置面板 / 404 正文 4 张，另含四条实测断言——折叠 rail 几何闭合、分割线两侧留白、字段尾部操作位的容器包含与留白、composer 尾部按钮同一条上下边。`capture:surfaces` 的 32 张图含动画表面，只作人工复核，不是自动判据。CI 前端跑在 windows-latest，基线名带 `-win32` 才能对上；判据浏览器固定为 `@playwright/test` 锁定的 Chromium（**不再用系统 Edge**，见阶段十七），换 Playwright 版本等于换 oracle，重生成基线要按一次环境变更对待。
2. **WebGL 不入像素基线**：`BrandMetaballs` 在 `prefers-reduced-motion` 下 `speed=0`（shader 会彻底停 rAF），但 GPU 与 SwiftShader 输出不保证逐像素一致，404 基线刻意只框正文块，品牌球用几何断言把关。
3. **持久层禁令的适用面（本轮未收口，已定方案未执行）**：`..domain..`、`..api..`、`..application..` 三条只禁 `com.baomidou.mybatisplus..` 与 `org.apache.ibatis..`。Lombok 与 Spring 的 `DataIntegrityViolationException`/`DuplicateKeyException` 仍在 application 使用，属有意保留：前者是编译期代码生成，后者是 Spring 的可移植异常翻译，不是 ORM 细节。**适用面缺口**：规则按包名命中，模块根下的用例类不在射程内。本轮按职责复核了 7 个中招的类，结论是它们**不是同一类东西**：`assets/AssetService`、`assets/AttachmentService`、`llm/ModelProfileService` 是真正的用例（策略），应经仓储端口取数；而 `jobs/BackgroundJobService`、`jobs/BackgroundJobRecoveryService` 是持久队列机制本身（12+4 处 wrapper 是租约与原子状态转移 SQL），`assets/StalePendingAssetReconciler` 与 `llm/ProfileCapabilities` 是存储侧的清扫与查询助手。给后四类强插端口只会得到与 SQL 一比一镜像的假抽象。**阻塞点**：`assets`/`llm` 的行类型（`Asset`、`StoredAttachment`、`ModelProfile`）同时被当作领域对象返回给调用方，端口签名绕不开它们——所以这三个用例的端口化必须先做 `position`/`identity`/`resume` 那套 `*Entity` 分离，是一整块工作，不能半做。同理 `artifact`/`interview` 的 7 个 `BaseMapper<领域类>` 无 `@TableName`，表名完全依赖 `application.yml:53` 的隐式驼峰转换且无测试守护。**本轮补充**：这条不能就地补注解——那 7 个类住在 `*/domain/`，而 `DOMAIN_STAYS_FRAMEWORK_FREE` 禁止 `..domain..` 依赖 mybatis/ibatis；只能先做 `*Entity` 分离，再补注解，再加"mapper 泛型参数必须带 `@TableName`"的机检（见阶段十九末条）。同轮已修掉的是 `integration` 整包导出：三个 DB 端口移入不导出的 `interview.application.repository`，voice 改依赖 `InterviewSessionGuard.isOngoing`。
4. **本轮排查出、未处理的其余留存**：`elevated-*` 判定为有意分层（见阶段十三首条）；派生 token 的巧合等值（`--layout-brand-mark-inline-size` 等 4 处）；`.tsx` 内的几何数值仍不在 `verify-ui-tokens` 射程（该脚本只遍历 `.css`），新加的 `OVERLAY_OFFSET` 靠 `verify:ui` 的"只能取该对象成员"这条间接把关，对象本身的 4 个数字没有判据；`capture:surfaces` 不在 CI 跑，`manifest.json` 的 revision 落后于 HEAD 且无人比对；像素门禁仍没有 re-baseline 通道（失败证据已能下载，但基线只能在开发机重生成，见阶段十七）；3 个配置键只在 `@Value` 内联默认（`prelude.voice.turn-pool-size`、`prelude.jobs.lease-duration-seconds`/`heartbeat-interval-seconds`）；线程池 5/20/100 等硬编码；`InMemoryRetrievalAdapter.indices` 与 `RealtimeConnectionRegistry.connections` 无上限无 TTL；`"report.generate"` 字面量 4 处而 `JobTypes.REPORT_GENERATE` 零引用；`LlmPurpose`、`JobStatusResponse` 零引用；`.gitattributes` 只覆盖 `frontend/**` 与 workflows，`backend/**`、`docs/**` 行尾不受约束；`backend/Dockerfile` 以 root 运行、无 HEALTHCHECK 且 CI 从不构建。
5. **论文 Mermaid 架构图同步时机**：若正式清理 4 个空包（`agent`、`tools`、`telemetry`、`settings`），需按 `thesis-assets/meta/workflow-governance.md` 对 [`thesis-assets/evidence/diagrams/`](file:///e:/Prelude/thesis-assets/evidence/diagrams/) 做项目漂移复核。本轮未触碰 `thesis-assets/**`。

### 论文风险移交清单（需作者决策，不由代码会话代答）

| 风险 | 现状 | 为什么是论文层面而非工程层面 |
| :--- | :--- | :--- |
| 证据链与新事实脱节 | 阶段一至四的结论（sentrux 门禁被删除、方案 A 完成度、token 体系重构、后端分层禁令）均未回写进 `thesis-assets/chapters/*.md` | 正文唯一真相源与证据锁定顺序由论文工作流管辖，工程侧不得反向改写正文 |
| "治理门禁提升质量"的论证前提变化 | 原论证部分依赖 sentrux 分数回落；该门禁现已删除，改为按职责拆分 + 自建实测门禁 | 需要重新选定可辩护的度量口径，不能沿用旧分数叙述 |
| 新增自研门禁的定位 | `verify:cascade`（实测层叠顺序、穿透 `cn()` 的死原子）、`verify:tokens` 双向引用检查是本轮新增能力 | 是否作为论文贡献点陈述、以及与既有 lint 体系的边界，属学术表述决策 |
| 视觉度量口径 | 像素基线 44 张（实验台按面板亮/暗 28 张 + 产品面按面亮/暗 12 张 + Prompt Bar / 模型菜单 / 设置面板 / 404 正文 4 张），平台锁定 win32 | 论文若声称"全界面视觉回归覆盖"仍与实际不符：面试**对话态**（消息流与 composer 在真实会话中的组合）无产品面基线，着色品牌球与 echarts 画布被 mask 后改由几何断言把关，需按真实覆盖面表述 |
