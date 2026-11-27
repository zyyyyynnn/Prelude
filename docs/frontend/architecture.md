# 前端架构

## Runtime 与工具链

| 层           | 当前契约                                                                                                           |
| ------------ | ------------------------------------------------------------------------------------------------------------------ |
| 应用形态     | React SPA；React Router 管理路由与 URL，TanStack Query 管理服务端状态，临时状态保留在最接近使用位置的 React 组件中 |
| 统一工具入口 | Vite+ 驱动 `dev`、`check`、`build`、`preview` 与测试                                                               |
| 生产构建     | Rolldown 与 Oxc；目标 `es2023`                                                                                     |
| 格式与 Lint  | Oxfmt 与 Oxlint；`vp check` 包含 type-aware lint 和 tsrs 类型检查                                                  |
| 样式         | Tailwind CSS 4、Base UI 与 Prelude-owned source                                                                    |
| 开发代理     | `/api` 代理到 `127.0.0.1:8080`，开启 WebSocket；请求头在代理边界移除 Origin                                        |

```text
frontend/src/
├── app/       启动、Provider 装配、路由与根布局，以及开发态组件实验台（`app/lab`）
├── features/  auth、assets、resume、position、interview、report、analytics、settings、profile
├── shared/    品牌资源、设计 token、纯工具与 Prelude-owned UI source
```

依赖方向是 `app -> features -> shared`。`shared` 不依赖 feature、路由实例或服务端状态模块；feature 不依赖 `app`；feature 内部互相取用走相对路径，不绕开自身组织。

## 公共入口

| 公共面       | 入口                                    | 消费规则                                                                                                        |
| ------------ | --------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| 每个 feature | `frontend/src/features/<name>/index.ts` | 调用点取 `@/features/<name>`；入口只再导出确有外部消费者的具名符号                                              |
| 设计系统     | `frontend/src/shared/ui/index.ts`       | 调用点取 `@/shared/ui`；primitive 的视觉、语义 variant 与组合接口由 Prelude 维护                                |
| feature 内部 | 具名文件                                | 视图、解析与实现留在具名文件；例如 `report` 的 `parse.ts`、`report-view.tsx`、`report-sections.tsx`、`print.ts` |
| 组合根例外   | `app/`，不含 `app/lab/`                 | 可深导入入口已登记的具名文件与符号，用于路由级独立 chunk；登记同时证明该名字有消费者                            |

入口之外的调用方只取入口导出的名字。`app/lab` 是展示面，不决定加载边界，因此不享有组合根深导入例外。静态 barrel 与按路由深导入的边界由 `verify:architecture` 的 AST 读取器验证。

## Feature Ownership

| Feature     | 职责                                                                                                                                     |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `auth`      | 登录、注册、OAuth provider 读取与 Session 客户端状态                                                                                     |
| `assets`    | 面试附件上传、删除与附件类型契约                                                                                                         |
| `resume`    | 简历列表、上传/删除与面试上下文契约                                                                                                      |
| `position`  | 内置岗位读取与用户岗位管理                                                                                                               |
| `interview` | 开面配置、会话、文字流、语音编排与报告入口                                                                                               |
| `report`    | 报告解析、展示与 PDF 打印导出                                                                                                            |
| `analytics` | 面试趋势、能力分数与薄弱点；目录名与组件 `AnalyticsPage`、路由 `/analytics`、接口 `/api/analytics/*` 与后端 `AnalyticsQueryService` 一致 |
| `settings`  | 主题与模型配置的管理入口；简历数据归 `resume`、岗位数据归 `position`、账户资料归 `profile`，`settings` 只做跨模块管理入口                |
| `profile`   | 头像解码与账户 Profile 展示                                                                                                              |

## 状态所有权

| 状态                       | Owner                    | 约束                                                                                                                                 |
| -------------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| 服务端资源、缓存、重试     | TanStack Query           | response 留在 Query cache；默认 `staleTime=30s`、`retry=1`、不因窗口聚焦自动重取；query key 由各 feature 的 `query-keys.ts` 集中定义 |
| 当前页面、筛选与可分享导航 | React Router 与 URL      | 目标会话先获取成功再提交 URL；失败保留当前会话并提供原位重试，以较新选择为准                                                         |
| 临时交互与表单草稿         | React local state        | 不把可分享导航或服务端真源复制到长期本地状态                                                                                         |
| 派生值                     | props、URL 或 Query 结果 | 直接计算，不建立第二份缓存                                                                                                           |

模型 Configuration 属于账户级全局配置；面试会话保存开面时冻结的模型与思考深度快照。账户主体变化或 Session 失效时，`auth` 先卸载当前账户资源，再取消并清空 Query cache。

### Auth 状态与不可用语义

| 状态/信号                    | 当前行为                                                                                                                             |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `checking`                   | `/api/auth/me` 启动探测完成前                                                                                                        |
| `authenticated`              | 已取得 account id                                                                                                                    |
| `anonymous`                  | 未登录；仅启动探测的 401 映射为匿名，不触发全局过期清理                                                                              |
| `unavailable`                | 启动探测网络失败、非 OK 响应或畸形数据；保留原路由并提供重试，不误判为未登录                                                         |
| `expired`                    | 普通 API 返回 401 后进入匿名并标记过期；登录页显示一次失效提示                                                                       |
| `503 rate_limit_unavailable` | Redis 限流不可用时，登录/注册接口返回 Problem Detail；前端显示服务端 detail 的系统 toast，不把认证不可用渲染成密码错误或表单内联错误 |
| 账户切换                     | 先取消 Query、清空 cache，再装载新主体；迟到响应不得跨账户复用                                                                       |

流式回答失败后，以服务端会话快照恢复消息。报告只接受完整核心结构；结构不合法时按纯文本原样展示。`generating` 会话每 2 秒刷新查询，`report_ready` 只作为提前展示通知，耐久报告仍以查询结果为准。

## UI Source 与所有权

| 范围                | 契约                                                                                                                                                                   |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primitive authority | Base UI 提供 Dialog、Popover、Menu、Tooltip、Combobox、Select、Focus 与 Keyboard 行为                                                                                  |
| Prelude source      | `shared/ui` 存放实际采用并由 Prelude 维护的源码，每类交互对应一套 primitive                                                                                            |
| shadcn 采用         | Button、Field 与表单控件采用 shadcn source ownership 结构                                                                                                              |
| Prompt Bar          | 采用 [Beautiful UI](https://www.beautifului.dev/) 组合模式；来源记录在 `frontend/beautiful-ui.sources.json`；负责附件、简历、岗位、JD 与模型选择，管理动作进入设置弹窗 |
| 样式归属            | `shared/styles/index.css` 注册的 chrome 只能有一个 markup owner；产品界面与组件实验台调用同一实现                                                                      |
| Feature 样式        | feature 目录不含 CSS 文件；`app/styles.css` 装配 Tailwind、扫描范围与共享样式                                                                                          |

被多个 feature 复用的通用表面归 `shared/ui`：

| 类别           | 组件                                                                                                                                                                      |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 基础动作与字段 | `Button`、`Field`、`FieldActions`、`FieldAction`、`Input`、`Textarea`、`Select`、`SegmentedControl`、`HiddenFileInput`                                                    |
| 导航与布局     | `SidebarFrame`、`SidebarBrand`、`SidebarAction`、`SidebarPane`、`NavItem`、`PageHeader`、`ScrollRegion`                                                                   |
| 表面与反馈     | `Panel`、`SubSection`、`Card`、`InsetCard`、`OptionCard`、`ScoreTile`、`LoadingState`、`EmptyState`、`ErrorState`、`GeneratingSurface`、`FeedbackProvider`、`useFeedback` |
| 浮层与菜单     | `Dialog`、`IconTooltip`、`DropdownMenu` 及菜单项、`MenuLabel`                                                                                                             |
| 消息与面试输入 | `MessageBubble`、`PromptBar`、`ContextAttachment`、`PromptBarFact`、`PromptBarToggle`、`PromptBarModelTrigger`、`PromptBarActions`、`VoiceLevelMeter`                     |
| 图标           | `Icon` 与 `IconGlyph`；裸 `<Ri… />` JSX 渲染被禁止                                                                                                                        |

只服务一个领域视图的件留在该 feature；确有跨 feature 或实验台消费者时才进入该 feature 的 barrel：

| Feature     | 当前公共导出中的可复用件                                                 |
| ----------- | ------------------------------------------------------------------------ |
| `resume`    | `ResumeRow`、简历取数与 query keys；入口提供管理面板的懒加载包装         |
| `position`  | `PositionRow`、岗位取数与 query keys；入口提供管理面板的懒加载包装       |
| `report`    | `StructuredReport`、`ReportPanel`、报告打印与报告类型                    |
| `settings`  | `SettingsNavigation`、`ThemeChoiceGroup`、设置上下文、主题与模型配置取数 |
| `profile`   | Profile 读写与 query keys；入口提供资料面板的懒加载包装                  |
| `interview` | 模型配置类型；composer、session list 与流处理留在具名文件                |

组合器把状态留在内部时，实验台冻结呈现走无状态呈现层，例如 `AnswerComposerSurface`。feature 负责取数与把领域类型映射成原始 props；`AppShell` 与面试、报告页保留查询与回调的薄封装。模型配置 query key 包含 account id，旧账号的保存、发现与能力检测回调不得写入新账号缓存。

组件实验台位于 `app/lab/ComponentLab.tsx`，由 `main.tsx` 的 DEV-only 路由挂载。它属于组合根，组合 feature 的公开导出并渲染真实组件；样例数据在 `app/lab/samples.ts`，按角色命名，口径见 `DESIGN.md`。

## 命名规约

| 范围           | 规约                                                                                                                   |
| -------------- | ---------------------------------------------------------------------------------------------------------------------- |
| 自动分屏取数   | `fetch*`                                                                                                               |
| 受管存储改写   | `read*` / `write*`                                                                                                     |
| 列表过滤       | `get*`                                                                                                                 |
| DOM/事件处理   | `handle*`                                                                                                              |
| Query 加载态   | 以数据源为准，使用 `isPending`                                                                                         |
| 样式治理       | BEM 表达组件内部结构；依赖 `:hover`/`:focus` 的状态注册为 `@utility`；跨组件状态用 Tailwind `group`/`peer` 与 `data-*` |
| primitive 前缀 | `shared/ui` primitive 使用 `ui-`                                                                                       |
| 产品命名空间   | 保留 `prelude-theme-change`、`prelude-theme-preference`、`prelude-user-id`、`prelude_schema`                           |
| 新边界         | 优先使用 `data-slot`                                                                                                   |

## 验证器

| 验证器                                           | 断言                                                                                                                                                                                                            |
| ------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `verify:architecture`                            | 源码只位于 `app`/`features`/`shared`；依赖方向与跨 feature 入口；公共入口只再导出具名符号；每个导出有外部消费者；组合根深导入必须命中登记文件与名字；图标只经 `Icon` 渲染；Beautiful UI 来源的 `localPath` 存在 |
| AST 读取范围                                     | `from`、`import()`、`require()` 与再导出都算深导入；相对路径先解析到 `src` 内目标模块再判定                                                                                                                     |
| `verify:oxc`                                     | Oxc parser、resolver、transform、Isolated Declarations 与 minify 在真实入口通过，压缩产物小于转换产物                                                                                                           |
| `npm run check`                                  | Oxfmt、Oxlint type-aware lint 与 tsrs 类型检查                                                                                                                                                                  |
| `verify:ui` / `verify:tokens` / `verify:cascade` | CSS owner、token、层叠与渲染面契约，完整断言见 `docs/quality/ui-quality-system.md`                                                                                                                              |
