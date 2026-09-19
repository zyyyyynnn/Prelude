# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。

| 门禁 | 验证范围 |
| --- | --- |
| `npm run check` | Vite+ 统一的格式、Oxlint type-aware lint 与 TypeScript 类型检查 |
| `npm run verify:architecture` | 前端目录、依赖方向与 CSS owner 边界 |
| `npm run verify:ui` | 颜色旁路、原生 Tooltip/Confirm 与交互动效禁用项；样式表卫生（禁止 feature CSS 文件与 feature 样式导入、空规则、未分层元素选择器、无消费者的类规则） |
| `npm run verify:tokens` | token 登记完整性与唯一性；声明但无任何消费者的 token；被引用但从未声明的 `var(--x)` 与 `atom-(--x)`（未声明的自定义属性会让整条声明在计算值阶段静默失效）；CSS 规则中的裸值（阴影、字重、边框宽度、绝对长度 px/rem）。相对单位 `%`/`em`/`vh`/`vw` 放行，技术必需的裸值（forced-colors 描边、`sr-only` 1px 裁剪盒）须在规则内标 `geometry-exempt: <理由>` 显式豁免 |
| `npm run verify:cascade` | 用构建产物实测同一元素上「注册 utility × 核心原子 / 未分层类」的同属性冲突，以及未分层类必然压过核心原子造成的死原子；须在 `npm run build` 之后执行 |
| `npm run verify:production` | 生产产物不包含开发态组件检查面 |
| `npm run verify:byok` | 四种 provider 协议暴露、设置交互与精确 DTO 行为 |
| `npm run verify:dark` | 暗色偏好启动恢复 |
| `npm run verify:a11y` | 真实浏览器 Axe 检查 |
| `npm run verify:visual` | 代表性桌面界面、空状态、设置面、Prompt Bar 多级菜单与 Tooltip 对比度，以及组件实验台（亮/暗）与 404 面的像素基线 |
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
| `shadcn/no-restyle` | error | 禁止用 className 改写 DS 外观。对 `Button` / `Input` / `Textarea` / `Select` / Menu 项 / `IconTooltip` / `Dialog` / `SegmentedControl` / `Panel` 配置 contracts：**仅允许 layout**，并 deny `spacing` / `color` / `typography` / `shape` / `effects` 中与组件所有权冲突的类别 |
| `shadcn/no-unknown-classes` | error | 禁止 Tailwind 无法生成的类名；界面样式只能是原子类或 `index.css` 中注册的 `@utility` |

**例外范围（仅组件实现层）**：`src/shared/ui/**` 关闭 `no-restyle`、`no-arbitrary-values`、`require-static-classes`——primitive 自身拥有样式；调用点仍受上述 contracts 与 token 规则约束。`no-raw-colors` 与 `no-inline-styles` 在实现层仍生效。

`no-unknown-classes` 只识别 `src/shared/styles/index.css` 生成的类，因此复合样式必须注册为该文件内的 `@utility`，feature 目录不含 CSS 文件；注册命名与层叠次序约定见 `DESIGN.md` 的 Style Assembly。

Tooltip 由 Base UI 提供交互行为，并使用高对比中性表面。页面和组件使用既有 Prelude token，不建立局部色板。
