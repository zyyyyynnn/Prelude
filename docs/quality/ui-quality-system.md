# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。验证命令见 `docs/setup.md#验证`。

## 门禁

| 门禁 | 断言 |
| --- | --- |
| `npm run check` | Vite+ 统一的格式、Oxlint type-aware lint 与 TypeScript 类型检查 |
| `npm run verify:architecture` | 前端目录、依赖方向与 CSS owner 边界。入口契约按 AST 读：`from`、`import()`、`require()` 与再导出四种写法都算一次深导入，且相对路径先解析到 src 内的目标模块再判定。`app/` 之外只能取 `@/features/<name>` 或 `@/shared/ui`；`app/` 是组合根，可以点名 feature 里**已被该 feature 入口登记**的具名文件，未登记的直接失败；登记同时充当"这个名字有人消费"的证据 |
| `npm run verify:ui` | 禁止 feature CSS 文件与 feature 样式导入；空规则；未分层元素选择器（元素级重置须在 `@layer base`，浏览器外观覆写按 `/* browser-chrome: <理由> */` 具名豁免，标记与规则正文按完整选择器文本比对）；无消费者的类规则；功能 `@utility name-*` 必须有字面量调用点；单一拥有者配方（登记表 + 发现制：一段 ≥3 个类 token 且点名设计系统的 `className` 字面量跨 ≥2 个文件即失败，例外须具名并写明提升目标）；`ui-*__*` 与 `workspace-header__*` 不得在组件外写出；`sideOffset` 只能取 `OVERLAY_OFFSET` 成员 |
| `npm run verify:tokens` | token 登记完整性与唯一性；声明但无任何消费者的 token；被引用但从未声明的 `var(--x)` 与 `atom-(--x)`；CSS 规则中的裸值（阴影、字重、边框宽度、绝对长度）按**声明**读取，跨行的 `calc()`、`@utility` 内的自定义属性、混用 `var()` 的值都在范围内，`var(--x, 0px)` 的回退值不算尺寸；`derived_tokens` 必须仍是引用其来源的表达式；盒尺寸不得整值借用与某档 `--ui-glyph-*` 等值的 `--spacing-*` 步骤；读取器发现的声明数低于阈值即失败。相对单位 `%`/`em`/`vh`/`vw` 放行，技术必需的裸值（forced-colors 描边、`sr-only` 1px 裁剪盒）须在规则内标 `geometry-exempt: <理由>`。样式表之外的几何由 markup 读取器覆盖：`.tsx` 里的 Tailwind 任意值与无单位内联样式；图表内部几何以具名 allowlist 豁免，且每个条目必须仍在源码里存在 |
| `npm run verify:cascade` | 用构建产物实测同一元素上「注册 utility × 核心原子 / 未分层类」的同属性冲突，以及未分层类必然压过核心原子造成的死原子；含经 `cn(base, className)` 注入的原子。须在 `npm run build` 之后执行 |
| `npm run verify:production` | 生产产物按 chunk 断言不含开发态组件检查面的路由标识符，并断言这些标识符在源码树中仍然存在（改名即红） |
| `npm run verify:byok` | 四种 provider 协议暴露、设置交互与精确 DTO 行为 |
| `npm run verify:dark` | 暗色偏好启动恢复：两种 scheme 的解析值**不同**，且 `dark` 类先于 `#root` 出现 |
| `npm run verify:a11y` | 真实浏览器 Axe，覆盖 `/interview` 与 `/components-lab` 两个面 × 亮/暗。两条都先等**被测页面自己画出**的 landmark 再扫描；判据保留 tags 覆盖的全部严重级（`wcag2a/aa/21a/21aa` 报出的多是 `serious`）；失败时打印 impact、规则 id、选择器与 axe 给出的原因文本 |
| `npm run verify:visual` | 44 张 `*-win32.png` 像素基线：组件检查面按面板逐张（亮/暗各 14 张，高于视口的面板先按实测差额扩窗再取图，WebGL 品牌球 mask 后改用几何断言）、产品面按面逐张（登录、注册、面试准备态、看板、产品内报告面、设置主题面板，亮/暗各 6 张）、404 面与窄桌面布局各一张。另有四条实测断言：折叠 rail 的容器宽度/行盒/图标间隙与 token 闭合、每个分割线两侧间隙不小于 `--spacing-sm`、字段尾部操作位落在控件盒内、composer 尾部操作簇内所有按钮共享同一条上下边。两条页面扫描都同时报告**自己量到了多少个目标**（分割线还单独报告"被测试的那块区域里量到几个"），量到 0 个即失败 |
| `npm run test:smoke` | React 开发 StrictMode 下真实浏览器核心行为与客户端路由 |

新增或修改判据必须红测：把要防的缺陷种进去、看到 FAIL、再恢复看到 PASS。探针前把目标文件复制到仓库外，恢复用复制，不要 `git checkout`。

## shadcn/lint

经 Oxlint 插件 `@shadcn/lint`（`vite.config.ts` → `lint.rules`）接入 `npm run check`。组件识别前缀为 `@/shared/ui`；错误提示统一指向 `DESIGN.md` 与 `frontend/src/shared/styles/index.css`。

| 规则 | 级别 | 约束 |
| --- | --- | --- |
| `no-raw-colors` | error | 禁止色板色类名；只用 Prelude 语义 token |
| `no-arbitrary-values` | error | 禁止任意值 utility；使用 DESIGN 间距/圆角/尺寸阶梯 |
| `no-inline-styles` | error | 禁止内联样式与 `<style>`；仅允许文档化的运行时 CSS 变量（当前 `--score-fill`） |
| `require-static-classes` | error | 类名必须为静态字面量，门禁可读 |
| `no-restyle` | error | 禁止用 className 改写 DS 外观。对 `Button` / `Input` / `Textarea` / `Select` / Menu 项 / `IconTooltip` / `Dialog` / `SegmentedControl` / `Panel` 配置 contracts：**仅允许 layout**，并 deny `spacing` / `color` / `typography` / `shape` / `effects` 中与组件所有权冲突的类别 |
| `no-unknown-classes` | error | 禁止 Tailwind 无法生成的类名；界面样式只能是调用点的原子类、`index.css` 中注册的 `@utility`，或承担组件内部结构与 `@internal` 归属 chrome 的顶层未分层类 |

例外范围（仅组件实现层）：`src/shared/ui/**` 关闭 `no-restyle`、`no-arbitrary-values`、`require-static-classes`——primitive 自身拥有样式；调用点仍受上述 contracts 与 token 规则约束。`no-raw-colors` 与 `no-inline-styles` 在实现层仍生效。

`no-unknown-classes` 只识别 `src/shared/styles/index.css` 生成的类，因此复合样式必须注册为该文件内的 `@utility`，feature 目录不含 CSS 文件。Tailwind 从源码文本读类名：只在查表里存在的类名不会产出任何规则，所以页面壳这类类名必须字面出现在调用点。注册命名与层叠次序约定见 `DESIGN.md` 的 Style Assembly。

测试选择器只用 `data-slot`、`role` 与语义文本；需要以类名定位时，该类必须是 `index.css` 里注册的 `@utility`。

## 判据浏览器

`@playwright/test` 锁定的 Chromium，不用系统 Edge。换 Playwright 版本等于换 oracle，要按一次环境变更处理并重生成基线。
