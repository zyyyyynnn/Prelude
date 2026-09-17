# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。

| 门禁 | 验证范围 |
| --- | --- |
| `npm run check` | Vite+ 统一的格式、Oxlint type-aware lint 与 TypeScript 类型检查 |
| `npm run verify:architecture` | 前端目录、依赖方向与 CSS owner 边界 |
| `npm run verify:ui` | 颜色旁路、原生 Tooltip/Confirm 与交互动效禁用项 |
| `npm run verify:tokens` | token 声明完整性、基础控件不变量、语义阴影与层级唯一性 |
| `npm run verify:production` | 生产产物不包含开发态组件检查面 |
| `npm run verify:byok` | 四种 provider 协议暴露、设置交互与精确 DTO 行为 |
| `npm run verify:dark` | 暗色偏好启动恢复 |
| `npm run verify:a11y` | 真实浏览器 Axe 检查 |
| `npm run verify:visual` | 代表性桌面界面、空状态、设置面、Prompt Bar 多级菜单与 Tooltip 对比度 |
| `npm run build` | Vite+ 生产构建 |
| `npm run test:smoke` | React 开发 StrictMode 下真实浏览器核心行为与客户端路由 |
| `npm audit --omit=dev` | 生产依赖漏洞门禁 |

## shadcn/lint 样式约束

经 Oxlint 插件 `@shadcn/lint`（`vite.config.ts` → `lint.rules`）接入 `npm run check`。组件识别前缀为 `@/shared/ui`；错误提示统一指向 `DESIGN.md` 与 `frontend/src/shared/styles/index.css`。

| 规则 | 级别 | 约束 |
| --- | --- | --- |
| `shadcn/no-raw-colors` | error | 禁止色板色类名；只用 Prelude 语义 token |
| `shadcn/no-arbitrary-values` | error | 禁止任意值 utility；使用 DESIGN 间距/圆角/尺寸阶梯 |
| `shadcn/no-inline-styles` | error | 禁止内联样式与 `<style>`；仅允许文档化的运行时 CSS 变量（当前：`--report-score-fill`） |
| `shadcn/require-static-classes` | error | 类名必须为静态字面量，门禁可读 |
| `shadcn/no-restyle` | warn（调用点） | 禁止用 className 改写 DS 外观；全局因 BEM 页面类保持 warn。对 `Button` / `Input` / `Textarea` / `Select` / Menu 项 / `IconTooltip` / `Modal` / `SegmentedControl` 配置 contracts：**仅允许 layout**，并 deny `spacing` / `color` / `typography` / `shape` / `effects` 中与组件所有权冲突的类别 |
| `shadcn/no-unknown-classes` | warn | Tailwind 无法生成的类名；与 BEM 选择器架构冲突，保持 warn |

**例外范围（仅组件实现层）**：`src/shared/ui/**` 关闭 `no-restyle`、`no-arbitrary-values`、`require-static-classes`——primitive 自身拥有样式；调用点仍受上述 contracts 与 token 规则约束。`no-raw-colors` 与 `no-inline-styles` 在实现层仍生效。

Tooltip 由 Base UI 提供交互行为，并使用高对比中性表面。页面和组件使用既有 Prelude token，不建立局部色板。
