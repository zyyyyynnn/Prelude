# UI Quality System

`DESIGN.md` 是 UI 规范的唯一最高入口。本文件只描述当前 UI 代码组织与自动化验证，不重新定义色板、token 数值或视觉语言。

## 代码映射

| 层级 | 路径 | 职责 |
| --- | --- | --- |
| Foundations | `frontend/src/shared/ui/styles/index.css`、`frontend/tokens/ui-tokens.json` | token 定义、基础样式与只读 token 索引 |
| Components | `frontend/src/shared/ui/` | Reka UI 驱动的通用 primitive |
| Patterns | `frontend/src/features/*/components/` | 由 primitive 组成的业务界面 |
| Lab | `frontend/src/devtools/component-lab/` | 仅开发态的组件状态检查 |

样式入口移动或模块拆分必须保持现有 token 值和用户可见视觉不变。业务组件不得反向定义 Foundations。

## 命令

```powershell
npm --prefix frontend run check
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:flows
npm --prefix frontend run verify:a11y
npm --prefix frontend run verify:visual
```

## CI 门禁

| Gate | 范围 | CI 类型 |
| --- | --- | --- |
| `check` | Oxfmt、Oxlint、`vue-tsc --noEmit` | blocking |
| `verify:architecture` | 四层目录、依赖方向、feature 公共入口、旧目录回流 | blocking |
| `test:contracts` | 会话偏好迁移与 Provider DTO 精确字段 | blocking |
| `build` | Vite+ / Rolldown 生产构建 | blocking |
| `verify:production` | 生产产物不包含 devtools，PDF vendor 不被首屏预加载 | blocking |
| `verify:ui` | 静态 UI guardrail | blocking |
| `verify:tokens` | token schema、唯一性与 design-lock 值 | blocking |
| `verify:byok` | BYOK 设置流程 | blocking |
| `verify:dark` | 暗色主题基本行为 | blocking |
| `verify:flows` | 会话偏好迁移、确认/取消、隐藏持久化与刷新恢复 | blocking |
| `verify:a11y` | axe critical 与键盘路径 | blocking |
| `capture:visual` | 视觉场景截图 | artifact-only |

CI 浏览器测试复用 Windows runner 的 Microsoft Edge channel，并设置 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`。本地未设置 `CI` 时使用 Playwright 默认浏览器；本地专用配置可显式使用 Edge。

## 静态 Guardrail

`verify:ui` 阻断以下回流：

- `transition-all`、`window.confirm`、原生 `title=`；
- 未批准的阴影、边框、硬高度、arbitrary px 与 magic ratio；
- 业务组件中的非 token 颜色和裸像素；
- scoped `:focus-visible` 绕过 `--shadow-icon-action-focus`。
- Tooltip 回退到页面 surface，或缺少统一的中性反相表面与长文本换行约束。

`verify:tokens` 校验：

- `frontend/tokens/ui-tokens.json` schema 完整性；
- `--shadow-*` 原始值只位于 token 定义块；
- `--z-index-*` 数值唯一；
- 已锁定布局与控件尺寸和 `frontend/src/shared/ui/styles/index.css` 一致。

## Component Lab

`/components-lab` 由 `frontend/src/app/router.ts` 在 `import.meta.env.DEV` 为真时条件注册。生产构建不包含该路由或 `frontend/src/devtools/` 模块。

Lab 覆盖 Button、Input、Textarea、Select、DropdownMenu、Combobox、Dialog、Tooltip、Badge、Card、EmptyState、SegmentedControl、Workspace、Composer 与 Message 等稳定状态。

## 浏览器覆盖

`verify:a11y` 使用 mock API 执行登录页、工作区、设置弹窗、下拉控件、侧栏、Composer 和结构化报告的 axe 与键盘路径检查。门禁只阻断 critical violation；绿色结果不代表不存在 serious color-contrast 问题，也不授权修改现有品牌色或 token 值。

`verify:visual` 覆盖浅色/暗色登录、侧栏、工作区空态、文字/语音 Composer、设置页、下拉浮层、Tooltip 对比度、报告、数据看板、Component Lab、移动端报告与 PDF 导出。CI 的 `capture:visual` 只上传 artifact，本地 `verify:visual` 用于明确的视觉回归验证。

## 相关文档

- `DESIGN.md`：视觉、交互与 token 最高规范
- `docs/frontend/architecture.md`：前端四层架构与模块所有权
- `docs/quality/local-review-checklist.md`：本地交付检查
