# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。验证命令见 `docs/setup.md#验证`。

## 门禁

| 门禁 | 断言 |
| --- | --- |
| `npm run check` | 格式、Oxlint type-aware lint 与 TypeScript 类型检查 |
| `npm run verify:architecture` | 前端目录、依赖方向与 CSS owner 边界。入口契约按 AST 读：`from`、`import()`、`require()` 与再导出都算一次深导入，相对路径先解析到 src 内目标模块再判定。`app/` 之外只能取 `@/features/<name>` 或 `@/shared/ui`；`app/` 可以点名 feature 入口已登记的具名文件，登记同时充当「这个名字有人消费」的证据 |
| `npm run verify:ui` | feature CSS 文件与 feature 样式导入为零；空规则为零；未分层元素选择器为零（元素级重置在 `@layer base`，浏览器外观覆写按 `/* browser-chrome: <理由> */` 具名豁免，选择器全文锁在登记表，标记与规则正文按完整选择器文本比对）；无消费者的类规则为零；功能 `@utility name-*` 必有字面量调用点；单一拥有者配方（登记表 + 发现制：一段 ≥3 个类 token 且点名设计系统的 `className` 字面量跨 ≥2 个文件即失败，例外具名并写明提升目标）；`ui-*__*` 与 `workspace-header__*` 只在组件内写出；`sideOffset` 只取 `OVERLAY_OFFSET` 成员；`shared/ui` 的 `cn()` 不出现插值拼接类名 |
| `npm run verify:demo-copy` | 演示 harness 与后端策略文案镜像一致 |
| `npm run verify:tokens` | token 登记完整且唯一；声明且有消费者的 token；被引用且已声明的 `var(--x)` 与 `atom-(--x)`；CSS 规则中的裸值（阴影、字重、边框宽度、绝对长度）按声明读取，跨行 `calc()`、`@utility` 内自定义属性、混用 `var()` 的值都在范围内，`var(--x, 0px)` 的回退值不算尺寸；`derived_tokens` 仍是引用其来源的表达式；盒尺寸与某档 `--ui-glyph-*` 等值时指名该 glyph token；读取器声明数低于阈值即失败。`%`/`em`/`vh`/`vw` 放行；技术必需的裸值在规则内标 `geometry-exempt: <理由>`。样式表之外的几何由 markup 读取器覆盖：`.tsx` 里的 Tailwind 任意值与无单位内联样式；图表内部几何以具名 allowlist 豁免，且每个条目仍在源码里存在 |
| `npm run verify:cascade` | 用构建产物实测同一元素上「注册 utility × 核心原子 / 未分层类」的同属性冲突，以及未分层类压过核心原子造成的死原子；含经 `cn(base, className)` 注入的原子。在 `npm run build` 之后执行 |
| `npm run verify:production` | 生产产物按 chunk 不含开发态组件检查面的路由标识符，且这些标识符仍在源码树中（改名即红） |
| `npm run verify:byok` | 四种 provider 协议暴露、设置交互与精确 DTO 行为 |
| `npm run verify:dark` | 暗色偏好启动恢复：两种 scheme 的解析值不同，且 `dark` 类先于 `#root` 出现 |
| `npm run verify:a11y` | 真实浏览器 Axe，覆盖 `/interview` 与 `/components-lab` × 亮/暗。每条先等被测页面画出的 landmark 再扫描；判据保留 tags 覆盖的全部严重级；失败时打印 impact、规则 id、选择器与原因文本 |
| `npm run verify:visual` | 44 张 `*-win32.png` 像素基线：组件检查面按面板逐张（亮/暗各 14 张，高于视口的面板先按实测差额扩窗再取图，WebGL 品牌球 mask 后改用几何断言）、产品面按面逐张（登录、注册、面试准备态、看板、产品内报告面、设置主题面板，亮/暗各 6 张）、404 面与窄桌面布局各一张。另有四条实测断言：折叠 rail 的容器宽度/行盒/图标间隙与 token 闭合、每个分割线两侧间隙不小于 `--spacing-sm`、字段尾部操作位落在控件盒内、composer 尾部操作簇内所有按钮共享同一条上下边。页面扫描报告自己量到的目标数（分割线另报区域内在范围数），量到 0 个即失败 |
| `npm run test:smoke` | React 开发 StrictMode 下真实浏览器核心行为与客户端路由 |

新增或修改判据先红测：种入要防的缺陷、看到 FAIL、恢复后看到 PASS。探针前后用仓库外文件副本保存与恢复目标文件。

## shadcn/lint

经 Oxlint 插件 `@shadcn/lint`（`vite.config.ts` → `lint.rules`）接入 `npm run check`。组件识别前缀为 `@/shared/ui`；错误提示指向 `DESIGN.md` 与 `frontend/src/shared/styles/index.css`。

| 规则 | 级别 | 约束 |
| --- | --- | --- |
| `no-raw-colors` | error | 类名取 Prelude 语义 token |
| `no-arbitrary-values` | error | utility 取 DESIGN 间距/圆角/尺寸阶梯（含 CSS 变量简写） |
| `no-inline-styles` | error | 样式在 CSS；运行时 CSS 变量限文档化名单（当前为空） |
| `require-static-classes` | error | 类名为静态字面量 |
| `no-restyle` | error | className 只取 contract 允许的类别。contracts：`Button`、`SegmentedControl`、`Panel` 仅允许 layout；`ScrollRegion` 允许 layout、spacing 与 typography（滚动 chrome 与焦点停点归组件，盒子、留白与正文排版归调用方）；`Input`/`Textarea`/`Select`/Menu 项/`IconTooltip`/`Dialog` 允许 layout，deny 与组件所有权冲突的类别 |
| `no-unknown-classes` | error | 类名是调用点原子、`index.css` 注册的 `@utility`，或承担组件内部结构与 `@internal` chrome 的顶层未分层类 |

`src/shared/ui/**` 关闭 `require-static-classes`；`no-restyle`、`no-arbitrary-values`、`no-raw-colors` 与 `no-inline-styles` 在实现层仍生效。

`no-unknown-classes` 只识别 `src/shared/styles/index.css` 生成的类，复合样式注册为该文件内的 `@utility`。注册命名与层叠次序见 `DESIGN.md` 的 Style Assembly。

测试选择器取 `data-slot`、`role` 与语义文本；以类名定位时该类是 `index.css` 里注册的 `@utility`。

## 判据浏览器

`@playwright/test` 锁定的 Chromium（`playwright.config.ts` 无 `channel`）。Playwright 版本变更按一次环境变更处理并重生成基线。
