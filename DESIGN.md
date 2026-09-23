# Prelude UI 设计规范

本文件是 Prelude 前端视觉、交互与数据展示的最高规范。技术栈为 React、Base UI 与 Tailwind CSS。

## 视觉基础

Prelude 使用克制的暖色纸感视觉。页面背景、组件表面、文字、边界、焦点、圆角、阴影、间距、字号、动效和层级由 `frontend/src/shared/styles/index.css` 中的 Prelude Design Tokens 驱动。

### Color

- `--color-bg`：全局纸感背景。
- `--color-surface`：组件与浮层表面。
- `--color-surface-hover`：hover 表面。
- `--color-surface-muted`：弱强调表面。
- `--color-text-primary`、`--color-text-secondary`、`--color-text-tertiary`：三级文本。三级文本是上下文层级，在其实际落到的每个表面（`--color-surface`、`--color-surface-muted`）上都满足 WCAG AA（4.5:1）；浅色取值 `#666561`（muted 4.73:1 / surface 5.43:1）。轻量化只用色值表达。
- `--color-brand`：品牌强调。
- `--color-accent-solid`、`--color-accent-solid-hover`、`--color-accent-text`、`--color-text-on-accent`：从品牌色派生的交互角色，分别用于实心动作、悬停、选中态文本与其前景；小号文本与实心动作的对比度职责归 accent 角色。
- `--color-border`、`--color-border-warm`：边界。
- `--color-focus-field`、`--color-focus-action`：字段与动作焦点。
- `--color-error`：错误与破坏性动作。

组件颜色使用 CSS var、Tailwind token utility 或 `color-mix()`。基础色值集中在 token 定义中。浅色与暗色模式通过同一语义 token 映射。

### Spacing And Size

控件高度全项目一档：`--ui-height-control` 36px，落在 4px 网格上，由 `tokens/ui-tokens.json` 的 `design_lock_values` 锁定。按钮、输入框、选择器、菜单行、字段尾部图标动作、导航项、分段控件轨道与 prompt bar 控件全部使用这一档。需要更轻的视觉重量时调整内边距与颜色，高度保持不变。

浮在控件内部的元素用 `--ui-control-inset` 2px 与宿主留出光学间隙，并按同心规则取圆角：`宿主圆角 - --ui-control-inset`。SegmentedControl 的滑块保持整格宽度、上下各缩 2px，圆角取 `--radius-md - 2px`，四周间隙均匀；字段尾部图标动作同理，盒尺寸为 `--ui-height-control - 2 × --ui-control-inset`。控件的命中区域始终等于整档高度。

间距分三档，一个数值只服务一种关系：

- `--spacing-sm` 8px 是绑定关系：标签与其控件、控件与其说明、标题与其副标题、分组标题与其内容、导航项之间、列表行之间、同一控件的分项之间。
- `--spacing-md` 16px 是同一区域内的并列块：字段与字段、按钮行与按钮行、卡片与卡片、面板小节与小节。
- `--spacing-lg` 24px 是区块边界：表面的内边距、面板标题行与其内容区、页面内面板与面板。

块与块的间距只由容器 `gap` 给出。

容器若为容纳另一处几何而存在，尺寸由那处几何推导（折叠侧栏宽度 = 一枚控件 + 两侧 gutter + 自身边框，写作 `calc()`）；阅读宽度、视口下限、纹理平铺等只能靠肉眼判定的值保持字面量。登记在 `tokens/ui-tokens.json` 的 `derived_tokens` 里的 token 仍引用其来源表达式。

图标与图标按钮的盒尺寸使用 `--ui-glyph-sm|md|lg`（16/20/24）；与某一档 glyph 等值的盒子指名该 glyph token。标准边界使用 `--border-width-default`。布局宽度、区块高度与内容行宽使用 `--layout-*` 与 `--content-*`。`--layout-composer-reserve-block-size` 是消息流为浮动 composer 预留的上限，值不可推导。层与行高只走 `--z-index-*` 与 `--line-height-*` 命名空间。浮层与锚点的间距集中在 `shared/ui/positioning.ts` 的 `OVERLAY_OFFSET`（tooltip 比菜单飘得更远）。仅两例光学偏移（按下位移、附件删除盒与 chip 的负叠）在规则内标 `geometry-exempt`。

图表内部几何以具名常量留在图表模块内（`TREND_GRID` 的 `grid` 留白、`lineStyle.width`、雷达 `radius`/`strokeWidth` 是为图表内容量出的尺寸），字体与颜色经 `cssVarNumber()` 读取设计 token。

控件内的图标尺寸由 CSS 拥有：`.ui-button__content`、`.field-action`、`.row-action`、`.ui-dialog__close`、`.ui-toast__close` 与 `.ui-toast [data-icon]` 下的 `svg` 取 `--ui-glyph-sm`；SVG 的 `width` 表现属性优先级低于 CSS。会话行置顶角标是脱离控件的装饰图形，尺寸由调用点的 `size` 决定。

固定格式控件通过稳定高度、宽度或 grid track 保持布局。文本在容器内自然换行或截断，并由 Tooltip 提供完整值。

### Radius And Shadow

- 小型控件使用 `--radius-sm` 或 `--radius-md`。
- Dialog 等大型表面使用 `--radius-lg` 或 `--radius-xl`。
- Dropdown、Select、Combobox、Tooltip 与 Toast 使用 `--shadow-whisper`。
- Dialog 与 Confirm 使用 `--shadow-modal`。

组件通过 shadow token 获得层级，单个表面保持一层边界与一层阴影。

### Typography

- 品牌、标题、表单控件与关键操作使用 `--font-serif`。
- 正文、说明和数据文本使用 `--font-sans`。
- 代码、日志和 token 名称使用 `--font-mono`。
- 字号使用 `--font-size-xs` 至 `--font-size-2xl` 阶梯，字重使用 `--font-weight-*` 语义阶梯，行高使用 `--line-height-*` 语义阶梯，组件内部采用紧凑标题尺度。

阶梯同时以原子类暴露：`text-xs|sm|md|lg|xl|2xl`、`leading-solid|display|tight|heading|compact|base|relaxed|copy`、`font-regular|medium|semibold`，全部指向上述 token。

标题、指标、标签与正文的常见配对固定为十三个语义角色，页面选择角色：

| 角色 | 用途 | 组合 |
| --- | --- | --- |
| `type-eyebrow` | 标题上方的引导标签 | serif `xs` / medium / `tight` / tertiary |
| `type-metric` | 大号数字指标 | serif `xl` / medium / `display` / primary |
| `type-hero` | 页面级响应式大标题（认证、终态页、面试空态） | serif `clamp(xl, 5vw, 2xl)` / medium / `display` / primary |
| `type-document-title` | 文档面主标题（报告的 `h1`） | serif `xl` / semibold / `display` / primary |
| `type-title` | 区块主标题 | serif `lg` / medium / `tight` / primary |
| `type-subtitle` | 次级标题 | serif `md` / medium / `compact` / primary |
| `type-label` | 字段与条目名称 | serif `sm` / medium / `compact` / secondary |
| `type-body` | 成段正文 | sans `sm` / regular / `relaxed` / secondary |
| `type-copy` | 阅读正文（copy 行高） | sans `sm` / `copy` / secondary |
| `type-caption` | 图注与气泡时间戳 | serif `xs` / tertiary（行高取 Tailwind `text-xs` 自带值） |
| `type-reading` | 报告正文（报告阅读宽度） | sans `sm` / `copy` / secondary / `--content-report-reading-max-inline-size` |
| `type-lead` | 页面导语（正文 + 阅读宽度） | sans `sm` / regular / `relaxed` / secondary / `--layout-lead-max-inline-size` |
| `type-meta` | 计数、时间、辅助说明 | sans `xs` / regular / `compact` / tertiary |

行高由字号决定：同一字号只对应一种行高。角色表收的是重复配对；一处出现的组合用原子类显式写，同一组合在两个及以上拥有者里重复才升为角色；调用点使用角色名。

标题角色与 DOM 层级一一对应：`h1` 用 `type-hero`（页面主标题）、`type-document-title`（打印文档面，不随视口放大）或 `workspace-header__title`，`h2` 用 `type-title`，`h3` 用 `type-subtitle`，字段与条目名用 `type-label`。`Panel` 按 `level` 选出 `h2`/`h3` 与对应角色。

排版取值以实际渲染值为准。

### Motion

动效使用 `--motion-duration-*` 与 `--motion-ease-standard`。颜色与表面变化使用 token transition；进入和退出优先 opacity 与 transform。几何动画采用 `transform`，加载态保持控件尺寸稳定，并支持 `prefers-reduced-motion`。

### Style Assembly

界面样式只有三种写法，自上而下层叠：未分层类 > utilities 层 > `@layer base`。

1. **调用点 Tailwind 原子类**。`flex`、`gap-md`、`bg-surface`、`rounded-lg` 能表达的，就用原子。
2. **`frontend/src/shared/styles/index.css` 中具名注册的 `@utility`**。承载嵌入 `var()` 的多值简写、`auto-fit`/`minmax` 轨道、来自 token 的 logical border、跨元素状态传播、`@keyframes`，以及页面壳与命名 chrome。只组合通用 token 的 utility 取通用名（`nav-item`、`list-row`、`elevated-modal`）；写死某个 owner 布局 token 的带该 owner 前缀（`sidebar-pane`、`prompt-bar-input`、`report-columns`）。
3. **顶层未分层类**。只承担两类职责：组件自己的 BEM 内部结构（`ui-menu__item`、`ui-button__content`、`workspace-header__*`）与由 `/* @internal src/<owner>.tsx */` 声明归属的整段 chrome；以及浏览器外观覆写（autofill 底色与文字色、Blink/Edge 的 `::-ms-reveal`/`::-ms-clear`、Chrome 的 credentials 按钮），各带 `/* browser-chrome: <理由> */`，按完整选择器文本登记豁免。调用点可覆写的几何由原子与 utilities 承担。

页面壳（`workspace-page*`、`app-layout*`、`app-sidebar`、`page*`）与滚动槽（`scrollable`、`gutter-stable`）一律是 `@utility`。`feature` 目录不含 CSS 文件；`features/*` 与 `app/shell` 只使用 token 与原子类。`frontend/src/app/styles.css` 装配 Tailwind、扫描范围与共享样式；`shared/styles/index.css` 拥有 token、主题、重置、全局排版、焦点状态、复合 utility 与文档级打印策略。

- 跨组件状态用 Tailwind `group/*`、`peer/*` 与 `data-*` 变体；文档级行为（报告打印）留在 `index.css` 的 `@media print`，以 `data-slot` 为锚点。
- 同一元素上叠加「注册 utility × 核心原子」或「utility × 未分层类」时，谁生效由 Tailwind 内部排序决定，视为缺陷。需要覆盖时只有两种写法：为基座声明 `base-variant` 命名的变体 utility，或把该属性从基座拆出交给调用点独占。
- 元素级重置（`button`、`a`、标题与列表 margin）写在 `@layer base`，只承担字体族与 margin；字号与颜色由排版角色提供。
- 功能 utility（`@utility name-*`）的类名以源码字面量出现，调用点数用字面量分支表达并由类型收窄；拼接出的名字没有对应规则。
- 界面结构只有一份 JSX：`index.css` 为某种 chrome 注册了 utility，就有一个组件拥有那段标记，产品界面与组件实验台都调用它。拥有者按职责放在 feature 或 `shared/ui`。状态藏在组件内部时抽无状态呈现层（`AnswerComposerSurface`），产品容器供真状态、实验台供冻结状态。
- 带 `__` 的 BEM 元素类表明内部结构；为组件标记注册的 `@utility`（如 `prompt-bar-control`）用 `/* @internal src/<owner>.tsx */` 声明归属，只有该文件在 class 位置写出这个名字。公共词汇不带 `@internal`：排版与高程角色（`type-*`、`elevated-*`）、通用布局件（`gutter-stable`、`scrollable`、`inset-card`）、路由书写的页面壳（`workspace-page*`、`app-layout*`、`app-sidebar`、`page*`）。
- 字段尾部操作位归 `shared/ui/field.tsx` 的 `FieldActions` 与 `FieldAction`：外层留白由按钮数量推导，tooltip 与无障碍名共用同一个字符串。rail 的品牌位归 `SidebarBrand`。
- 实验台面板的 `description` 只写归属路径（`shared/ui/panel`）；设计契约只在本文出现。
- **脱敏的分寸**：调用方本来就传文案的槽位写成角色（`分区一`、`字段一`、`选项二`、`小节三`、`示例文本一`、`主要操作`）；产品组件焊死的词照原样显示（`发送`/`开始面试`、上下文与模型菜单条目、`JD 匹配`、`加载中`/`加载失败`、品牌名 `Prelude`）。报告标题走 `reportCopy` 入参、缺省即产品词条，实验台传 `小节一…小节八`。共享的是「有哪些条目」（`sections`、`themeOptions`、报告结构），不是条目上的字。
- 样张正文用纯占位（`示例文本一`），长短差异用来检查换行与行宽。排版面每行尾随的角色名（`· type-body`）与品牌名同属身份标识，保留。

## Components

Base UI 提供 Dialog、Popover、Menu、Tooltip、Combobox、Select、Focus 与 Keyboard 行为。Prelude-owned source 负责视觉、语义 variant 和组合接口。每类交互对应一套 primitive。

### Actions

Button variant 明确表达主操作、次操作、轮廓、轻操作和破坏性操作。图标操作使用熟悉图标与可访问名称。loading 状态保留原始宽高，文本和图标使用透明度或受控替换。

按钮高度全项目一档（`--ui-height-control`）。`size` 区分盒形状：`default` 为文本按钮，`icon` 为等边图标按钮。`shape` 表达不改变高度的内容形态：`action` 为固定不收缩的宽动作，`hold` 为按住说话。

### Fields

Input、Textarea、Select 与 Combobox 使用 `--color-surface` 表面、`--color-border-warm` 边界、token padding 与衬线字体。字段焦点改变现有边界颜色，错误状态使用 `--color-error`。

### Floating Surfaces

Dropdown、Select 与 Combobox 使用 `--color-surface`、`--color-text-primary`、`--color-border-warm`、`--radius-md` 和 `--shadow-whisper`。这一中性高对比表面保持文字清晰，也与品牌强调色分离。

Tooltip 反过来用文字色做底、表面色做字（`--color-text-primary` / `--color-surface`），配 `--radius-sm` 与 `--shadow-whisper`：贴在控件旁的一行短说明，反色让它不与所注解的表面混淆。

Tooltip 内容使用 `--font-size-xs`、token padding 和 `--content-tooltip-max-inline-size`。primitive 统一 trigger 间距和 opacity 动效。截断文字的定位锚点是完整交互控件。

Dialog、Confirm 与 Toast 使用同一表面语义；遮罩使用 `--mask-overlay`，Dialog 使用 `--shadow-modal`。`.ui-dialog` 自带 `--spacing-xl` 内边距，是可直接放内容的浮层；`--workspace` 变体是 full-bleed 壳层，内边距与分区由调用点拥有（设置面板即此形态）。壳层自身就是 `--color-surface`，分区共享这一底色——分割线只在 surface 上成立。Confirm 复用 `.ui-dialog` 的 chrome，覆写自身宽度（`--layout-confirm-max-inline-size`）与动作行：动作按钮等分铺满整行。

### Composition

`shared/ui/panel.tsx` 的 `Panel` 是「带标题的表面」的唯一实现：标题行同时拥有标题、副标题与右侧操作区，操作按钮与标题同行、由 flex 竖直居中。

- `layout="fill"` 用于铺满容器的高度型表面（设置弹窗的五个分区、工作台浮层）：标题行带下边界，内容区自带 `--spacing-lg` 内边距并独立滚动。
- `layout="card"` 用于随内容增高的自足块（组件实验台的每个面板、岗位管理的两块面板）：自带边界、圆角、`--spacing-lg` 内边距与 `elevated-whisper`，标题行与内容区之间同样是 `--spacing-lg`。
- `level` 决定标题用 `h2` 还是 `h3`，并随之选择 `type-title` 或 `type-subtitle`；`eyebrow` 是标题上方的 `type-eyebrow`，`description` 是标题下方的 `type-meta`，`actions` 是右侧操作区，`footer` 是带上下边界的底部动作条。
- 高程卡片只有一个拥有者：`shared/ui/card.tsx` 的 `Card`。`Panel layout="card"` 与不带标题行的同高程块（数据看板的记分卡）都经它渲染，圆角、边界、`--spacing-lg` 内边距与 `elevated-whisper` 只此一处；卡内堆叠间距由组件的 `stack` 决定。内嵌卡片是 `shared/ui/inset-card.tsx` 的 `InsetCard`（`ScoreTile` 与薄弱点条目共用）。

全项目只有一种细分割线：`--border-width-default` 1px + `--color-border`，只画在 `--color-surface` 上（侧栏主操作下方那条线即基准，线色对表面色对比度 1.10）。`--color-border-warm` 是控件自身的边，`--color-line-decor` 只服务落在页面底色上的装饰边缘（登录卡）。更强的分层感来自间距与标题层级。

一条分割线两侧都有留白，且两侧由同一个容器给出：线附着在上方块时，上方由该块的 `padding` 给出、下方由容器的 `gap` 给出；附着在下方块时反之。面板内部再分层时，小节容器写 `grid gap-sm border-t border-border pt-md`，配合父容器的 `gap-md` 让细线上下各 16px，小节标题与其控件按 `--spacing-sm` 8px 绑定。设置弹窗的「修改密码」与「高级设置」即此形态，组件实验台的 Field 面板给出同一份样例。

语音输入与输入区分离：文字框在两种模式下都常驻可编辑，识别出的转录只写进草稿、由候选人自己发送。尾部操作区两种模式同构——切换图标、（语音模式下）`按住说话`、`发送` 图标，全部 `--ui-height-control` 一方盒、右端收口。`发送` 用图标：旁边可能站着 `按住说话`，两个带词的实心按钮会互相抢权重，而层级已经由尺寸表达；空草稿时它带着禁用态常驻，操作区不因输入而重排。

录音反馈收在 `按住说话` 内部：按下时标签淡出、`VoiceLevelMeter` 覆盖在同一盒子里（标签继续撑宽，控件在手指下保持尺寸），处理中复用按钮自己的 loading 态，面试官语音回放期间按钮为禁用。reduced-motion 下电平只采样一帧，读数保留、泵动消失。

按压反馈保持几何稳定，用环影、颜色、内容替换表达。composer 尾部操作区的按钮共享同一条上边与下边。

重复条目行用 `list-row`（带边界的完整行）或 `row-label-end`（名称与尾部动作两端对齐），加载、空库与失败统一落到 `empty-state`。三态拥有者是 `shared/ui/empty-state.tsx` 的 `LoadingState`/`EmptyState`/`ErrorState`：加载态自带 `role="status"`，失败态一律给重试。页面区段用无框布局与受控内容宽度，需要明确边界的数据对象升级为 `card`。

侧栏分三层：`@utility sidebar-frame` 拥有 rail 的盒子（宽度、边框、表面、折叠宽度与过渡），`@utility app-sidebar` 只加页面锚定（sticky、层级、`100vh`，并把 frame 撑满高度），`@utility sidebar-rail` 只拥有内容作用域（图标内边距的推导，以及 `data-sidebar-label`、`data-sidebar-brand` 的折叠过渡）。

结构归 `shared/ui/sidebar.tsx` 的 `SidebarFrame`：brand 与折叠按钮、分隔线下的主操作、滚动中段与页脚。主操作下方分割线的下线由 frame 自己的 `gap-md` 给出。产品 shell 是 `app-sidebar > SidebarFrame`，实验台让同一个 `SidebarFrame` 直接落在 `bg-bg` 上。折叠状态由两处局部属性表达：frame 与 rail 的 `is-collapsed` 控制宽度和淡出，`SidebarToggle` 自身的 `data-collapsed` 控制两枚箭头的交叉淡入；图标规则以按钮自己的属性为锚点。

报告只以页面表面呈现：分栏是 `auto-fit minmax(200px, 1fr)`，三维评分三列并排需要约 632px 纸面内宽。检阅宽度取 `--layout-workspace-content-max-inline-size`；`Panel layout="card"` 的内边距会挤掉第三列。

## Accessibility

- 交互控件具备可访问名称与完整键盘路径。
- 键盘焦点使用可见的语义边界；字段、动作、选中与打开状态彼此独立。
- 会滚动且内容里可能没有任何可聚焦元素的容器，自己是焦点点（`tabIndex={0}`）。这条可达性由 `shared/ui` 的 `ScrollRegion` 拥有：`Panel` 的 `fill` 主体、`SidebarPane`、面试文字流 `TranscriptScroll`、看板与组件实验台的页面壳都经它渲染。组件画出 `overflow-y-auto` 时即承担该可达性。`focusable={false}` 用于该区域同时对辅助技术隐藏的场合。滚动槽预留（`gutter-stable`）由调用方选：页面壳自己已声明 `scrollbar-gutter`；会长出滚动条的裸列需要预留。块的留白归视图；`TranscriptScroll` 是薄 owner（两个视图共有的部分 + 由 `view` 指名的 end padding）。
- 图标装饰使用空替代文本；信息图像提供等价文本。
- 文本与交互目标在桌面布局中保持互不遮挡。
- 系统高对比度与 reduced motion 偏好保持可用。

## Source Adoption

shadcn 提供 Button、Field 与表单控件的源码组织，Base UI 提供浮层交互语义。[Beautiful UI](https://www.beautifului.dev/) Prompt Bar 组合用于面试输入区。品牌字体由 Fontsource 本地可变字体资产提供，运行时不依赖远端字体服务。组件视觉统一由本文件和 Prelude Design Tokens 定义。

## Validation

UI 改动验证命令以 `docs/setup.md#验证` 与 `docs/quality/ui-quality-system.md` 为准。视觉审查以本文件和 `frontend/src/shared/styles/index.css` 的 token 定义为准。
