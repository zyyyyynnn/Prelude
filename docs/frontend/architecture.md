# 前端架构

## Runtime

Prelude 前端是由 Vite 构建的 React SPA。React Router 管理路由与 URL，TanStack Query 管理服务端状态，组件状态保留在最接近使用位置的 React 组件中。

```text
frontend/src/
├── app/       启动、Provider 装配、路由与根布局
├── features/  auth、assets、resume、template、interview、report、insight、settings
├── shared/    品牌资源、设计 token、纯工具与 Prelude-owned UI source
└── devtools/  仅开发态组件检查面
```

依赖方向是 `app / devtools -> features -> shared`。当前 feature 保持扁平公共面；出现内部目录时，跨 feature 调用只能经过明确公共模块。`shared` 不依赖 feature、路由实例或服务端状态模块，`devtools` 不进入生产路由与产物。`verify:architecture` 在 CI 中阻止反向依赖和其他源码根目录。

## Feature Ownership

| Feature | 职责 |
| --- | --- |
| `auth` | 登录、注册与 Session 客户端状态 |
| `assets` | 面试附件上传、删除与附件类型契约 |
| `resume` | 简历列表、上传、删除与面试上下文契约 |
| `template` | 内置岗位读取与用户岗位管理 |
| `interview` | 开面配置、会话、文字流、语音编排与报告入口 |
| `report` | 报告解析、展示与 PDF 打印导出 |
| `insight` | 面试趋势、能力分数与薄弱点 |
| `settings` | 用户资料、简历、岗位、模型与主题的统一管理入口 |

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

样式组合由 `app/styles.css` 负责：它装配 Tailwind、应用扫描范围、共享样式、应用外壳样式和各 feature 样式。`shared/styles/index.css` 只拥有 token、主题、重置、全局排版、焦点状态和可复用 UI/layout primitive；业务页面的样式必须留在对应的 `features/*` 或 `app/shell` owner 中。`verify:architecture` 同时检查源码依赖和 CSS 本地 `@import`，阻止 shared 反向引入应用或 feature 样式。

## 验证

- `npm run typecheck`：严格 TypeScript 检查。
- `npm run lint`：ESLint、typescript-eslint 与 React Hooks 规则。
- `npm run build`：执行 Vite 生产构建；TypeScript 静态检查由 `npm run check` 负责。
- `npm run test:smoke`：在 React 开发 StrictMode 下通过真实浏览器验证核心行为与客户端路由。
- `npm run verify:architecture`：目录与依赖方向。
- `npm run verify:ui`、`verify:tokens`：UI 结构与 token 契约。
- `npm run verify:byok`、`verify:dark`：BYOK 与主题行为。
- `npm run verify:a11y`、`verify:visual`：浏览器可访问性、布局与 Tooltip 对比度。
- `npm audit --omit=dev`：生产依赖漏洞门禁。
