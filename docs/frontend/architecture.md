# 前端架构

## Runtime

Prelude 前端是由 Vite+ 统一驱动开发、检查、构建与预览的 React SPA。React Router 管理路由与 URL，TanStack Query 管理服务端状态，组件状态保留在最接近使用位置的 React 组件中。

```text
frontend/src/
├── app/       启动、Provider 装配、路由与根布局，以及开发态组件实验台（`app/lab`）
├── features/  auth、assets、resume、position、interview、report、analytics、settings
├── shared/    品牌资源、设计 token、纯工具与 Prelude-owned UI source
```

依赖方向是 `app -> features -> shared`。公共入口有两处，规则相同：每个 feature 的 `index.ts`，以及设计系统的 `shared/ui/index.ts`——调用点分别取 `@/features/<name>` 与 `@/shared/ui`。两者都只再导出确有外部消费者的符号；视图与解析留在具名文件里（`features/report` 即 `parse.ts` + `report-view.tsx` + `report-sections.tsx` + `print.ts`）。入口之外的调用方只取入口导出的名字。唯一例外是 `app/`：它是组合根，也是唯一决定「某个视图何时加载」的层，因此可以点名入口已登记的具名文件（`@/features/settings/SettingsModal`）；登记同时充当「这个名字有人消费」的证据。登记 + 组合根选择性深导入下入口 JS 为 209.58 kB（gzip 66.03 kB），四个路由件各自独立 chunk。`shared` 不依赖 feature、路由实例或服务端状态模块。内部互相取用走相对路径，绕开自己的 barrel。

## Feature Ownership

| Feature | 职责 |
| --- | --- |
| `auth` | 登录、注册与 Session 客户端状态 |
| `assets` | 面试附件上传、删除与附件类型契约 |
| `resume` | 简历列表、上传、删除与面试上下文契约 |
| `position` | 内置岗位读取与用户岗位管理 |
| `interview` | 开面配置、会话、文字流、语音编排与报告入口 |
| `report` | 报告解析、展示与 PDF 打印导出 |
| `analytics` | 面试趋势、能力分数与薄弱点；目录名与组件 `AnalyticsPage`、路由 `/analytics`、接口 `/api/analytics/*` 与后端 `AnalyticsQueryService` 一致 |
| `settings` | 用户资料、主题与面试设置的管理入口；简历数据归 `resume`、岗位数据归 `position`，`settings` 只做跨模块管理入口 |

## 状态所有权

| 状态 | Owner |
| --- | --- |
| 服务端资源、缓存、重试 | TanStack Query |
| 当前页面、筛选和可分享导航 | React Router 与 URL |
| 临时交互与表单草稿 | React local state |

Query response 保留在 Query cache；派生值由 props、URL 或 Query 结果直接计算。账号主体变化或 Session 失效时，`auth` 先卸载当前账号资源，再取消并清空 Query cache。模型配置属于账号级全局配置，面试会话保存开面时的模型与思考深度快照。

历史会话导航先获取目标会话，再提交 URL；失败时保留当前会话并提供原位重试，以较新选择为准。流式回答失败后，以服务端会话快照恢复消息。报告只接受完整核心结构；结构不合法时按纯文本原样展示。

## UI Source

Base UI 是对话框、弹出层、菜单、选择器、焦点和键盘行为的基础 primitive authority。`shared/ui` 存放实际采用并由 Prelude 维护的源码，每类交互对应一套 primitive。

`shared/ui` 中的 Button、Field 与表单控件采用 shadcn source ownership 结构，Modal、Menu 与 Tooltip 使用 Base UI。面试输入区的 Prompt Bar 采用 [Beautiful UI](https://www.beautifului.dev/) 组合模式，来源记录位于 `frontend/beautiful-ui.sources.json`。Prompt Bar 负责附件、简历、岗位、JD 与模型选择；管理动作统一进入设置弹窗。

划分边界以样式归属为准：`shared/styles/index.css` 里注册了 chrome 的表面，其 markup 只允许有一处拥有者，产品界面与组件实验台都调用它。被多个 feature 复用的通用表面归 `shared/ui`——`Panel`/`SubSection`、`Card`/`InsetCard`、`SidebarFrame`/`SidebarBrand`/`SidebarAction`/`SidebarPane`/`SidebarToggle`、`ScrollRegion`、`MessageBubble`、`GeneratingCard`/`GeneratingSurface`、`PromptBar`/`ContextAttachment`/`PromptBarFact`/`VoiceLevelMeter`、`FieldActions`/`FieldAction`、`NavItem`、`OptionCard`、`ScoreTile`、`LoadingState`/`EmptyState`/`ErrorState`、`MenuLabel`/`PromptBarActions`、`PageHeader`、`HiddenFileInput`。只服务一个领域视图的件留在该 feature 并由其 barrel 导出：`ResumeRow` 归 `features/resume`；`StructuredReport`/`ScoreCard`/`StagePerformanceList`/`Trait`/`TrainingPlan`/`SectionHeading`/`ReportSection` 归 `features/report`；`InterviewSetupComposer`/`AnswerComposerSurface`/`SessionGroup`/`SessionGroupLabel`/`SessionRow` 归 `features/interview`；`SettingsNavigation`/`ThemePreview`/`ThemeChoiceGroup` 归 `features/settings`。组合器把状态留在内部时，实验台冻结呈现走无状态呈现层（`AnswerComposerSurface`）。feature 负责取数与把领域类型映射成原始 props；`AppShell` 与面试、报告页保留查询与回调的薄封装。

组件实验台位于 `app/lab/ComponentLab.tsx`，由 `main.tsx` 的 DEV-only 路由挂载。它属于组合根，组合 feature 的公开导出并渲染真实组件；样例数据在 `app/lab/samples.ts`，按角色命名（见 `DESIGN.md` 的脱敏口径）。

样式三种写法的层叠、命名与归属规则见 `DESIGN.md` 的 Style Assembly。`frontend/src/app/styles.css` 装配 Tailwind 与扫描范围；feature 目录不含 CSS 文件。

## 命名规约

自动分屏取数用 `fetch*`，受管存储改写用 `read*`/`write*`，列表过滤用 `get*`，DOM/事件处理用 `handle*`。React Query 的加载态以数据源为准（`isPending`）。样式治理用 BEM 表达组件内部结构；依赖 `:hover`/`:focus` 的状态注册为 `@utility`，跨组件状态用 Tailwind `group`/`peer` 与 `data-*` 变体。`shared/ui` 里的 primitive 使用 `ui-` 前缀；非类名标识保留产品命名空间（`prelude-theme-change` 事件、`prelude-theme-preference` 存储键、`prelude-user-id`、`prelude_schema`）。新增边界优先使用 `data-slot`。
