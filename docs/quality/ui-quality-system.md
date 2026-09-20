# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。

| 门禁 | 验证范围 |
| --- | --- |
| `npm run check` | Vite+ 统一的格式、Oxlint type-aware lint 与 TypeScript 类型检查 |
| `npm run verify:architecture` | 前端目录、依赖方向与 CSS owner 边界 |
| `npm run verify:ui` | 颜色旁路、原生 Tooltip/Confirm 与交互动效禁用项；样式表卫生（禁止 feature CSS 文件与 feature 样式导入、空规则、未分层元素选择器、无消费者的类规则） |
| `npm run verify:tokens` | token 登记完整性与唯一性；声明但无任何消费者的 token；被引用但从未声明的 `var(--x)` 与 `atom-(--x)`（未声明的自定义属性会让整条声明在计算值阶段静默失效）；CSS 规则中的裸值（阴影、字重、边框宽度、绝对长度 px/rem）。绝对长度按**声明**读取，因此跨行的 `calc()`、`@utility` 内的自定义属性、混用 `var()` 的值都在范围内；`var(--x, 0px)` 的回退值不算尺寸；`derived_tokens` 登记的 token 必须仍是引用其来源的表达式；盒尺寸不得整值借用与某档 `--ui-glyph-*` 等值的 `--spacing-*` 步骤；读取器发现的声明数低于阈值即失败，防止解析器空转造成假绿。相对单位 `%`/`em`/`vh`/`vw` 放行，技术必需的裸值（forced-colors 描边、`sr-only` 1px 裁剪盒）须在规则内标 `geometry-exempt: <理由>` 显式豁免 |
| `npm run verify:cascade` | 用构建产物实测同一元素上「注册 utility × 核心原子 / 未分层类」的同属性冲突，以及未分层类必然压过核心原子造成的死原子；须在 `npm run build` 之后执行 |
| `npm run verify:production` | 生产产物不包含开发态组件检查面 |
| `npm run verify:byok` | 四种 provider 协议暴露、设置交互与精确 DTO 行为 |
| `npm run verify:dark` | 暗色偏好启动恢复 |
| `npm run verify:a11y` | 真实浏览器 Axe 检查 |
| `npm run verify:visual` | 代表性桌面界面、空状态、设置面、Prompt Bar 多级菜单与 Tooltip 对比度，以及 404 面的像素基线；组件检查面按面板逐张比对（亮/暗各 14 张，高于视口的面板先按实测差额扩窗再取图，WebGL 品牌球 mask 后改用几何断言）；另有两条实测断言——折叠 rail 的容器宽度/行盒/图标间隙与 token 闭合，以及每个分割线角色元素两侧的间隙不小于 `--spacing-sm` |
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

测试选择器只用 `data-slot`、`role` 与语义文本；需要以类名定位时，该类必须是 `index.css` 里注册的 `@utility`，否则 lint 认不出、构建也不产出。断言计算样式时对齐**实际渲染值**，转写原子类后必须重新读取确认。

Tooltip 由 Base UI 提供交互行为，并使用高对比中性表面。页面和组件使用既有 Prelude token，不建立局部色板。

## 几何漂移与防复发

反复出现过的一类缺陷：某个尺寸写成手调字面量，而它真正服务的是「容纳或对齐另一处几何」。改一侧不会让另一侧报错，漂移只在截图里可见。git 历史里的链条：

- `dac2f99` 写下推导式 `calc(控件 + gutter × 2 + 边框)`；`b114707` 做语义化 token 时把它拍平成 `51px`，同一提交里的行内 padding 却仍是推导式——容器冻结、内容继续推导。
- `2f4c2b4` 把控件高度四档合并为 36px，折叠容器仍 51px，图标向右溢出 2px；同一提交把只服务折叠态的 7px padding 常开化为 `--sidebar-btn-padding-inline`，新公式漏掉了按钮自身用于画焦点环的 1px 边框。
- `ff88b12` 用 `design_lock_values` 把 `51px` 锁住：锁「值不许动」不等于锁「值必须推导」，`6957e87` 的 Vue→React 迁移又把 5 条布局锁静默删掉，没有任何门禁报警。

现在的四道防线：

1. `verify:tokens` 按**声明**读取绝对长度（跨行值、`@utility` 内的自定义属性、混用 `var()` 的值都在范围内），并对 `tokens/ui-tokens.json` 的 `derived_tokens` 断言 token 仍是引用其来源的表达式。
2. 盒尺寸不得整值借用与某档 `--ui-glyph-*` 等值的 `--spacing-*` 步骤，否则间距阶梯一动它就跟着动。
3. `@visual` 的折叠 rail 断言把容器宽度、行盒、图标左右间隙逐项与 token 对齐，测试里不写任何尺寸字面量；把 token 改回 `51px` 会让该用例失败。
4. 实验台渲染产品同一组件（见 `DESIGN.md` 的 Style Assembly），近似 markup 会在截图对比时暴露，而不是被当成实现细节留下。

同批补上的两条判据盲区：组件检查面原先只有亮/暗各一张视口截图（只覆盖首屏），现按面板逐张比对，测试里的面板清单即覆盖契约，新增面板未登记会先失败在标题断言上；`capture:surfaces` 的 manifest 原先只记 `git rev-parse HEAD`，而图在提交前生成、revision 恒落后一个 commit，现额外记录工作树是否与提交一致，并在不一致时告警。

按面板比对后仍有一层盲区，且它让整页报告的基线长期只覆盖首屏：元素截图只能绘制滚动容器揭示的像素，而实验台面在 `.workspace-page__content.scrollable` 里，`clientHeight` 只有视口那么高。高于视口的面板会写出「尺寸正确、下半张空白」的基线——像素判据看似通过，实际从未看过报告的后两屏。现在测试先量出面板高度与容器可视高度的差额，按差额扩窗，再断言面板完整落在容器内，之后才取图像；面板再长也不会静默退回首屏。

判定后仍保留的字面量：`--layout-sidebar-header-block-size: 60px` 与 `--layout-page-center-block-offset: 84px` 表达的是「下限/留白」而非「等式」，推导成等值会改变观感，因此不登记进 `derived_tokens`。
