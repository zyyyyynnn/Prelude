# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。

| 门禁 | 验证范围 |
| --- | --- |
| `npm run check` | Vite+ 统一的格式、Oxlint type-aware lint 与 TypeScript 类型检查 |
| `npm run verify:architecture` | 前端目录、依赖方向与 CSS owner 边界。入口契约按 **AST** 读，不按文本：`from '@/…'`、`import('@/…')`、`require('…')` 与再导出四种写法都算一次深导入——早先只匹配 `from '@/<surface>/'` 子串的版本，在 `main.tsx` 用 `await import()` 加载六个路由里的五个时照样绿灯。边界：`app/` 之外只能取 `@/features/<name>` 或 `@/shared/ui`；`app/` 是组合根，可以点名 feature 里**已被该 feature 入口登记**的具名文件（这是保住路由级代码分割的那条缝），未登记的直接失败；登记同时充当"这个名字有人消费"的证据 |
| `npm run verify:ui` | 颜色旁路、原生 Tooltip/Confirm 与交互动效禁用项；样式表卫生（禁止 feature CSS 文件与 feature 样式导入、空规则、未分层元素选择器、无消费者的类规则，以及**功能 `@utility name-*` 必须有字面量调用点**——Tailwind 从源码文本读类名，运行时拼出的类名不会产出任何规则。前两项曾长期**根本不执行**：顶层规则遍历器在 `}` 处先做 `depth -= 1` 再调 `flush()`，而 `flush()` 断言 `depth === 1`，于是永远看不到刚关闭的那条规则；恢复顺序后它立刻报出 `html`、`body` 与 autofill 组三条裸元素选择器，三者已移入 `@layer base`）；**单一拥有者**：一组表重复到第二个拥有者后，其原子配方只允许出现在登记文件里——`className="empty-state"`（`shared/ui/empty-state`）、`type="file"`（`shared/ui/file-input`）、`border-t border-border pt-md`（`Panel` 的 `SubSection`）、`border-t border-border py-lg`（`ReportSection`）、`bg-surface-muted p-*`（`inset-card` utility）、`mx-sm text-xs font-semibold tracking-label`（`SessionGroupLabel`）、导语宽度 `max-w-(--layout-lead-max-inline-size)`（`type-lead` role）；**发现制重复配方**：一段 ≥3 个类 token 且点名了设计系统（颜色、圆角、高程、排版角色或已注册类名）的 `className` 字面量，跨 ≥2 个文件出现即失败——登记表只能记下已经发现的重复，这条负责找出下一个，逼它要么提升为唯一 owner、要么连同具体提升目标登记进例外清单（当前 1 条具名例外，PASS 行打印豁免数）；**内部类名不得外写**：`src/shared/ui/**` 与 `src/shared/styles/**` 之外出现任何 `ui-*__*` 或 `workspace-header__*` 即失败——调用方越过组件的 props 直接写它的元素类名，两份拷贝之后无从发现彼此漂移 |
| `npm run verify:tokens` | token 登记完整性与唯一性；声明但无任何消费者的 token（计数前先抹掉 `@theme` 里 `--x: var(--x)` 的自我镜像，并按 Tailwind 命名空间补认类名消费者——`--spacing-0` 由 `m-0` 消费、`--radius-lg` 由 `rounded-lg` 消费，只按 token 文本计数会把活 token 判死；shadcn 语义桥在 `ui-tokens.json` 显式登记为豁免）；被引用但从未声明的 `var(--x)` 与 `atom-(--x)`（未声明的自定义属性会让整条声明在计算值阶段静默失效）；CSS 规则中的裸值（阴影、字重、边框宽度、绝对长度 px/rem）。绝对长度按**声明**读取，因此跨行的 `calc()`、`@utility` 内的自定义属性、混用 `var()` 的值都在范围内；`var(--x, 0px)` 的回退值不算尺寸；`derived_tokens` 登记的 token 必须仍是引用其来源的表达式；盒尺寸不得整值借用与某档 `--ui-glyph-*` 等值的 `--spacing-*` 步骤；读取器发现的声明数低于阈值即失败，防止解析器空转造成假绿。相对单位 `%`/`em`/`vh`/`vw` 放行，技术必需的裸值（forced-colors 描边、`sr-only` 1px 裁剪盒）须在规则内标 `geometry-exempt: <理由>` 显式豁免。样式表之外的几何同样入范围：markup 读取器扫 `.tsx` 里的 Tailwind 任意值（`p-[7px]`）与无单位内联样式（`style={{ height: 40 }}`，React 按像素读）；图表内部几何以具名 allowlist 豁免（当前 `TREND_GRID`），且每个条目必须仍在源码里存在——陈旧条目是残留，不是豁免 |
| `npm run verify:cascade` | 用构建产物实测同一元素上「注册 utility × 核心原子 / 未分层类」的同属性冲突，以及未分层类必然压过核心原子造成的死原子；须在 `npm run build` 之后执行。"哪些类名算注册过的 utility"由 `scripts/utility-names.cjs` 单独拥有（`[a-z0-9_*-]+`），四个脚本共用同一个名字字符类；此前的四份副本都不含 `_`，于是 `workspace-page__content` 与 `app-layout__main` 这两个真实注册的页面壳被当成核心原子，它们身上的同属性冲突从未被测。A/B 实测：植入 `px-4` 冲突后，含 `_` 的版本报 `[padding/padding-inline] px-4 + workspace-page__content`，不含的版本读作干净 |
| `npm run verify:production` | 生产产物按 chunk 断言不含开发态组件检查面的路由标识符，并断言这些标识符在源码树中仍然存在（改名即红，不静默放行） |
| `npm run verify:byok` | 四种 provider 协议暴露、设置交互与精确 DTO 行为 |
| `npm run verify:dark` | 暗色偏好启动恢复 |
| `npm run verify:a11y` | 真实浏览器 Axe 检查，覆盖产品主链路 `/interview` 与组件实验台 `/components-lab` 两个面。两条都**先等渲染再扫描**：`goto()` 默认只等到 `load`，实测同一页面在扫描那一刻是 19 个 DOM 节点、渲染稳定后是 1235 个，不等就等于在扫一个空壳。判据保留 tags 覆盖的全部严重级（`wcag2a/aa/21a/21aa` 报出来的多是 `serious`），失败时打印 impact、规则 id 与选择器——此前"只留 `critical`"的写法恰好把这套规则能发现的东西全部滤掉 |
| `npm run verify:visual` | 代表性桌面界面、空状态、设置面、Prompt Bar 多级菜单与 Tooltip 对比度，以及 404 面的像素基线；组件检查面按面板逐张比对（亮/暗各 14 张，高于视口的面板先按实测差额扩窗再取图，WebGL 品牌球 mask 后改用几何断言）；产品面另按面逐张比对（登录、注册、面试准备态、看板、产品内报告面、设置主题面板，亮/暗各 6 张，共 12 张；其中登录/注册的着色品牌球与看板的 echarts 画布 mask 掉，栅格化表面在两次运行间不逐字节稳定）；另有四条实测断言——折叠 rail 的容器宽度/行盒/图标间隙与 token 闭合、每个分割线角色元素两侧的间隙不小于 `--spacing-sm`、字段尾部操作位必须落在控件盒内、垂直居中且输入框尾部留白不小于按钮宽度（登录页密码字段即被测点），以及 composer 尾部操作簇内所有按钮共享同一条上下边（按住态不得用位移表达反馈）。这两条扫描都同时报告**自己量到了多少个目标**，量到 0 个即失败——"没有违规"来自一次什么都没扫到的遍历，与真的干净是同一盏绿灯，这正是本文件反复记录的失效模式 |
| `npm run build` | Vite+ 生产构建 |
| `npm run test:smoke` | React 开发 StrictMode 下真实浏览器核心行为与客户端路由 |
| `npm audit --omit=dev` | 生产依赖漏洞门禁 |

## shadcn/lint 样式约束

经 Oxlint 插件 `@shadcn/lint`（`vite.config.ts` → `lint.rules`）接入 `npm run check`。组件识别前缀为 `@/shared/ui`；错误提示统一指向 `DESIGN.md` 与 `frontend/src/shared/styles/index.css`。

| 规则 | 级别 | 约束 |
| --- | --- | --- |
| `shadcn/no-raw-colors` | error | 禁止色板色类名；只用 Prelude 语义 token |
| `shadcn/no-arbitrary-values` | error | 禁止任意值 utility；使用 DESIGN 间距/圆角/尺寸阶梯 |
| `shadcn/no-inline-styles` | error | 禁止内联样式与 `<style>`；仅允许文档化的运行时 CSS 变量（当前：`--score-fill`） |
| `shadcn/require-static-classes` | error | 类名必须为静态字面量，门禁可读 |
| `shadcn/no-restyle` | error | 禁止用 className 改写 DS 外观。对 `Button` / `Input` / `Textarea` / `Select` / Menu 项 / `IconTooltip` / `Dialog` / `SegmentedControl` / `Panel` 配置 contracts：**仅允许 layout**，并 deny `spacing` / `color` / `typography` / `shape` / `effects` 中与组件所有权冲突的类别 |
| `shadcn/no-unknown-classes` | error | 禁止 Tailwind 无法生成的类名；界面样式只能是调用点的原子类、`index.css` 中注册的 `@utility`，或承担组件内部结构与 `@internal` 归属 chrome 的顶层未分层类 |

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

新增一批基线时，**先比对同一面的亮/暗两张 md5**：相同即说明暗色那帧从未真的切到暗色，基线只是亮色帧的副本，看着是判据其实什么都不判。产品面基线首版就踩了这一点——`open(page, scheme)` 的 `scheme` 只有 1/6 个实现读了它（TypeScript 允许少写参数，类型检查与全绿用例都不会异议）。修法不是给其余 5 个补 `if`，而是把 scheme 从各 `open` 收回、由循环统一应用并断言 `html` 上的 `dark` 类确实切换：参数被丢这件事从此不可表达。

判据浏览器固定为 `@playwright/test` 锁定的 Chromium，不用系统 Edge：`channel: 'msedge'` 把 oracle 变成"这台机器装了什么浏览器"，第一次 CI 跑就因为 `<textarea>` 右下角那 9×9 的 Blink 自绘 resize grip 红了 20/16 像素——设计体系并不拥有那些像素。换 Playwright 版本等于换 oracle，要按一次环境变更处理并重生成基线。

## 判据自身的失效方式

门禁也会坏，而且坏法是静默的。三种实测到过的形态：

- **恒真判据**：死类检查曾把全部源码拼成一个大字符串再 `includes(候选)`，而候选含 BEM 块名（`field__hint` → `field`），于是每个元素类永远算已消费，这条检查从未报出过任何东西。现在要求完整类名以词边界出现，豁免只给真正运行时拼接的族（全仓仅 `ui-button--${variant}`）。
- **空判据**：`@dark` 曾只断言 `--color-bg` 解析出的字符串非空——浅色模式下同样非空，删掉整个 `.dark` 块它照绿。现在断言的是两种 scheme 的解析值**不同**，以及时序（`dark` 类先于 `#root` 出现）。
- **看不见归属的形状匹配**：内部类泄漏检查只匹配 `*__*`，于是为一个组件注册的 `@utility`（`prompt-bar-control`）被调用点整段重写时毫无反应。归属是设计决定、无法从形状推导，现在由 `index.css` 里的 `/* @internal src/<owner>.tsx */` 声明并由 `verify:ui` 执行。机制上线时全仓只登记了 1 个家族，等于射程近乎为零；现在 70 个 `@utility` 中由单个组件的标记决定正确性的 31 个 chrome 家族都已声明拥有者，剩下的（排版与高程角色、通用布局件、路由自己书写的页面壳）按定义属于公共词汇，标了反而逼人绕开角色体系。

给判据做红测的方式：把要防的缺陷真的种进去，看它是否变红。上面三条都用人为改动验证过，其中一次探针本身是无效的——用 `setTimeout(…, 0)` 推迟主题应用并不会改变顺序，因为 React 初次挂载本来就晚于一个宏任务；换成足够长的延迟才看到预期的红。**反例不成立的实验不算验证。**

判定后仍保留的字面量：`--layout-sidebar-header-block-size: 60px` 与 `--layout-page-center-block-offset: 84px` 表达的是「下限/留白」而非「等式」，推导成等值会改变观感，因此不登记进 `derived_tokens`。
