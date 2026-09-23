# 前端架构

## Runtime

Prelude 前端是由 Vite+ 统一驱动开发、检查、构建与预览的 React SPA。React Router 管理路由与 URL，TanStack Query 管理服务端状态，组件状态保留在最接近使用位置的 React 组件中。

```text
frontend/src/
├── app/       启动、Provider 装配、路由与根布局，以及开发态组件实验台（`app/lab`）
├── features/  auth、assets、resume、position、interview、report、analytics、settings
├── shared/    品牌资源、设计 token、纯工具与 Prelude-owned UI source
```

依赖方向是 `app -> features -> shared`。公共入口有两处，规则相同：每个 feature 的 `index.ts`，以及设计系统的 `shared/ui/index.ts`。两者都只再导出**确有外部消费者**的符号，视图与解析各自留在具名文件里（`features/report` 即 `parse.ts` + `report-view.tsx` + `report-sections.tsx` + `print.ts`）。入口之外**不得点名内部文件**，唯一的边界是 `app/`：它是组合根，也是唯一决定"某个视图何时加载"的层，因此可以点名入口**已登记**的具名文件（`@/features/settings/SettingsModal`），未登记的（`@/features/settings/internal`）仍然红。这条边界是量出来的而非让步：六个路由全部改走 `@/features/<name>` 后，登录、设置、简历、岗位四块被提升进入口 chunk，首屏 JS 由 212.54 kB 涨到 240.69 kB（gzip 67.34 → 75.85 kB），未登录访客也要下载面试代码；登记 + 允许组合根点名的写法下入口为 209.58 kB（gzip 66.03 kB），四个路由件各自回到独立 chunk。`shared` 不依赖 feature、路由实例或服务端状态模块。`verify:architecture` 在 CI 中阻止反向依赖和其他源码根目录，并检查这条入口契约：入口文件只允许具名再导出、每个名字都要有入口之外的读取方、外部对内部文件的深导入（`from`、`import()`、`require()` 与再导出四种写法都算，靠 AST 而非文本匹配识别）、以及组合根点名的文件确已在入口登记；`shared/ui` 若根本没有 `index.ts` 也算违规而不是跳过。内部互相取用走相对路径，不绕自己的 barrel。

设计系统曾有 barrel（`main` 上 16 处消费），在 `53db447`「flatten feature public surfaces」中被一并删掉——那是审美决定而非技术约束，代价是此后 95 处调用点各自点名文件，改一个 primitive 要动多达 17 处无关代码，而同一时期 `features/*` 正被要求走 barrel。两套方向并存本身即是缺陷，故统一到入口一侧；当时的实测产物反而变小（JS 总量 1,247,345 → 1,238,394 字节），barrel 让打包器共享模块而不是按路由复制。

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

Query response 保留在 Query cache；派生值由 props、URL 或 Query 结果直接计算。账号主体变化或 Session 失效时，`auth` 先卸载当前账号资源，再取消并清空 Query cache，避免跨账号复用旧响应。模型配置属于账号级全局配置，面试会话保存开面时的模型与思考深度快照。

历史会话导航先获取目标会话，再提交 URL；失败时保留当前会话并提供原位重试，较早请求不得覆盖较新的选择。流式回答失败后，以服务端会话快照恢复消息。报告只接受完整核心结构；结构不合法时按纯文本原样展示，不推断分数或生成事实。

## UI Source

Base UI 是对话框、弹出层、菜单、选择器、焦点和键盘行为的基础 primitive authority。`shared/ui` 存放实际采用并由 Prelude 维护的源码，每类交互对应一套 primitive。

`shared/ui` 中的 Button、Field 与表单控件采用 shadcn source ownership 结构，Modal、Menu 与 Tooltip 使用 Base UI。面试输入区的 Prompt Bar 采用 [Beautiful UI](https://www.beautifului.dev/) 组合模式，来源记录位于 `frontend/beautiful-ui.sources.json`。Prompt Bar 负责附件、简历、岗位、JD 与模型选择；管理动作统一进入设置弹窗。所有 UI 源码使用 Prelude token 与 `DESIGN.md` 视觉语言。

划分边界以样式归属为准：`shared/styles/index.css` 里注册了 chrome 的表面，其 markup 只允许有一处拥有者，产品界面与组件实验台都调用它。被多个 feature 复用的通用表面归 `shared/ui`——`Panel`/`SubSection`、`Card`/`InsetCard`、`SidebarFrame`/`SidebarBrand`/`SidebarAction`/`SidebarPane`/`SidebarToggle`、`MessageBubble`、`GeneratingCard`/`GeneratingSurface`、`PromptBar`/`ContextAttachment`/`PromptBarFact`/`VoiceLevelMeter`、`FieldActions`/`FieldAction`、`NavItem`、`OptionCard`、`ScoreTile`、`LoadingState`/`EmptyState`/`ErrorState`、`MenuLabel`/`PromptBarActions`、`PageHeader`、`HiddenFileInput`；只服务一个领域视图的件留在该 feature 并由其 barrel 导出，例如 `ResumeRow` 归 `features/resume`、`StructuredReport`/`ScoreCard`/`StagePerformanceList`/`Trait`/`TrainingPlan`/`SectionHeading`/`ReportSection` 归 `features/report`、`InterviewSetupComposer`/`AnswerComposerSurface`/`SessionGroup`/`SessionGroupLabel`/`SessionRow` 归 `features/interview`、`SettingsNavigation`/`ThemePreview`/`ThemeChoiceGroup` 归 `features/settings`。组合器把状态留在内部时，实验台要冻结呈现就抽无状态呈现层（`AnswerComposerSurface` 供真状态与冻结状态两种调用），不在实验台复制标记。feature 仍只负责取数与把领域类型映射成原始 props，`AppShell` 与面试、报告页保留查询与回调的薄封装。`shared` 不引入 `app` 或 `features`，这条由 `verify:architecture` 强制。

组件实验台是开发者界面，位于 `app/lab/ComponentLab.tsx`，由 `main.tsx` 的 DEV-only 路由挂载。它属于组合根，因此可以像页面一样组合 feature 的公开导出，渲染真实组件而不是近似版；它的样例数据在 `app/lab/samples.ts`，一律按角色命名（见 `DESIGN.md` 的脱敏口径）。`verify:production` 按 chunk 断言开发专用路由的标识符不进入构建产物，并断言这些标识符在源码树中仍然存在：改名即失败，而不是让门禁静默放行。

样式有三种来源：调用点的 Tailwind 原子类、`shared/styles/index.css` 中具名注册的 `@utility`，以及顶层未分层类。未分层类是第三种写法而不是遗漏，只承担组件自己的 BEM 内部结构（`ui-menu__item`、`ui-button__content`）与由 `/* @internal src/<owner>.tsx */` 声明归属的整段 chrome；它位于 utilities 层之外因此压过后者——这正是它能钉住组件内部结构的原因，也是它不得声明调用点可能要覆写的几何的原因。`frontend/src/app/styles.css` 装配 Tailwind、应用扫描范围与共享样式；feature 目录不含 CSS 文件，`features/*` 与 `app/shell` 只使用 token 与原子类。`shared/styles/index.css` 拥有 token、主题、重置、全局排版、焦点状态、复合 utility 与文档级打印策略。`verify:architecture` 同时检查源码依赖和 CSS 本地 `@import`，阻止 shared 反向引入应用样式。视觉与层叠约定见 `DESIGN.md` 的 Style Assembly。

## 命名规约

远端读取用 `fetch*`、本地存储读写用 `read*`/`write*`、纯计算派生用 `get*`；DOM/事件处理用 `handle*`，动作为裸动词；加载态按层表达（数据源 `isPending`、控件 `loading`）。界面不使用 BEM 选择器：布局与外观由原子类表达，无法原子化的组合注册为具名 `@utility`，跨组件状态用 `group`/`peer` 与 `data-*` 变体传播。`shared/ui` 下新 primitive 统一 `ui-` 前缀（历史上是 `prelude-`，与产品名重复且和已有的 `ui-action`/`ui-field-control` 形成两套前缀，已合并为 `ui-` 一套）；测试与打印锚点使用 `data-slot`。

## 验证

完整验证命令以 `docs/setup.md#验证` 与 `docs/quality/ui-quality-system.md` 为准。
