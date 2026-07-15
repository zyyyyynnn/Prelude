# Frontend Architecture

Prelude 前端采用四层模块化单体。目录和依赖规则由 `npm --prefix frontend run verify:architecture` 自动强制。

## 目录

```text
frontend/src/
├── app/        # 启动、路由、根布局、Pinia 与 HTTP 装配
├── features/   # 业务域模块
├── shared/     # 无业务状态的通用能力与 UI primitive
├── devtools/   # 仅开发态工具
└── env.d.ts
```

不允许恢复顶层 `api/components/composables/lib/router/schemas/stores/styles/utils/views` 等兼容目录。

## 依赖方向

```text
app ───────► features ───────► shared
 │               │
 └──► devtools   └──► other feature public entry
```

- `shared` 不得依赖 app、feature、devtools、Pinia 或 Vue Router。
- feature 不得依赖 app 或 devtools。
- feature 内部使用相对路径；跨 feature 只能从 `@/features/<name>` 公共入口导入。
- `report` 不得导入 `interview`；报告数据必须由调用方传入。
- app 负责组合 feature；app 对 devtools 的引用只能位于 `import.meta.env.DEV` 条件分支。
- 公共入口 `features/<name>/index.ts` 只导出跨 feature 或 app shell 确实需要的类型、函数或组件，不承载业务逻辑。
- app 路由通过 `features/<name>/page.ts` 公共页面入口延迟加载页面，避免静态运行时入口使路由 chunk 失效。

## 模块所有权

| Feature | 所有权 |
| --- | --- |
| `auth` | 登录/注册 API、鉴权状态、认证 schema 与登录页 |
| `resume` | 简历列表、上传、结构化编辑、版本更新、改进建议动作与管理页 |
| `settings` | 用户资料、主题、LLM/BYOK draft、view state 与 actions |
| `interview` | 岗位资源、会话真源、文字/语音编排、工作区与开面流程 |
| `report` | 报告类型、解析、Markdown 展示、改进建议呈现组件与 PDF 导出；不发起简历写请求 |
| `insight` | 雷达、趋势、薄弱点数据与分析页 |

`report` 不读取 interview store。interview 只通过 `@/features/report` 传入报告数据、渲染报告或触发导出。PDF 实现通过动态 import 加载，`jspdf/html2canvas` 及其专用依赖位于延迟加载的 `vendor-pdf` chunk，不进入初始工作区主路径。

## HTTP 与类型

`shared/api/http.ts` 只提供 HTTP 客户端壳。`app/main.ts` 通过 `configureHttp({ getAccessToken, onUnauthorized })` 注入 auth token 与未授权跳转，因此 shared 不感知业务 store 或 router。

`shared/api` 只导出 `ApiResult`、`unwrapResult`、`ApiClientError` 与 HTTP 接口。业务 DTO 位于所属 feature 的 `model/types.ts`，不得建立跨域 contracts 汇总文件。

Provider 列表严格消费后端现有字段：`providerKey`、`displayName`、`availableModels`、`enabled`。前端不维护展示名映射，也不兼容臆造的 `providerName` 或 `models` 别名。`settings/model/providerProtocol.ts` 只描述三个固定 BYOK 协议的表单能力与端点后缀，不重复后端展示名或模型目录。

## 状态所有权

- auth token 由 `features/auth/model/authStore.ts` 持有，app 只在运行时装配中读取。
- 会话列表、活动会话、详情、报告文本与流取消句柄由 interview session store 持有。
- pin/hidden 偏好由独立 store 延迟 hydrate；读取不会发生在模块加载期。旧 `pinnedSessionIds` / `deletedSessionIds` 首次 hydrate 时迁移到 `prelude-interview-session-preferences`。
- `useInterviewPageController` 是工作区页面的编排接口；网络流、语音和报告监听仍由各自 composable 实现，简历建议动作通过 resume 公共入口调用。
- `useLlmSettings` 对组件只暴露 `draft`、`view`、`actions` 三组职责，不把请求细节散落到模板。

## UI 与开发工具

`shared/ui` 拥有通用 primitive，业务组合留在 feature。样式与 token 入口为 `shared/ui/styles/index.css`；架构整理不改变 `DESIGN.md` 的视觉规则或 token 基础值。

Component Lab 位于 `devtools/component-lab`，只在开发路由注册。生产构建不得暴露 `/components-lab` 或打包 devtools 模块。

## 自动化约束

`verify:architecture` 同时验证规则自身和真实源码，阻断：

- 非四层顶级源码目录；
- shared 或 feature 的反向依赖；
- shared 对 Pinia / Vue Router 的运行时依赖；
- 跨 feature 深导入；
- 只做旧路径转发的兼容文件。

该命令与 DTO 契约测试、关键会话流测试均为 CI blocking gate。
