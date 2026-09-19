# Prelude UI 设计规范

本文件是 Prelude 前端视觉、交互与数据展示的最高规范。技术栈为 React、Base UI 与 Tailwind CSS。

## 视觉基础

Prelude 使用克制的暖色纸感视觉。页面背景、组件表面、文字、边界、焦点、圆角、阴影、间距、字号、动效和层级由 `frontend/src/shared/styles/index.css` 中的 Prelude Design Tokens 驱动。

### Color

- `--color-bg`：全局纸感背景。
- `--color-surface`：组件与浮层表面。
- `--color-surface-hover`：hover 表面。
- `--color-surface-muted`：弱强调表面。
- `--color-text-primary`、`--color-text-secondary`、`--color-text-tertiary`：三级文本。
- `--color-brand`、`--color-brand-light`：品牌强调。
- `--color-accent-solid`、`--color-accent-solid-hover`、`--color-accent-text`、`--color-text-on-accent`：从品牌色派生的交互角色，分别用于实心动作、悬停、选中态文本与其前景；品牌本色不直接承担小号文本或实心动作的对比度职责。
- `--color-border`、`--color-border-warm`：边界。
- `--color-focus-field`、`--color-focus-action`：字段与动作焦点。
- `--color-error`：错误与破坏性动作。

组件颜色使用 CSS var、Tailwind token utility 或 `color-mix()`。基础色值集中在 token 定义中。浅色与暗色模式通过同一语义 token 映射。

### Spacing And Size

控件高度全项目只有一档：`--ui-height-control` 36px，落在 4px 网格上，由 DESIGN.md 与 `tokens/ui-tokens.json` 的 `design_lock_values` 共同锁定。按钮、输入框、选择器、菜单行、字段尾部图标动作、导航项、分段控件轨道与 prompt bar 控件全部使用这一档。需要更轻的视觉重量时调整内边距与颜色，高度保持不变。

浮在控件内部的元素用 `--ui-control-inset` 2px 与宿主留出光学间隙，并按同心规则取圆角：`宿主圆角 - --ui-control-inset`。SegmentedControl 的滑块保持整格宽度、上下各缩 2px，圆角取 `--radius-md - 2px`，因此四周间隙均匀；字段尾部图标动作同理，盒尺寸为 `--ui-height-control - 2 × --ui-control-inset`。控件的命中区域始终等于整档高度。

间距分三档，一个数值只服务一种关系：

- `--spacing-sm` 8px 是绑定关系：标签与其控件、控件与其说明、标题与其副标题、分组标题与其内容、导航项之间、列表行之间、同一控件的分项之间。
- `--spacing-md` 16px 是同一区域内的并列块：字段与字段、按钮行与按钮行、卡片与卡片、面板小节与小节。
- `--spacing-lg` 24px 是区块边界：表面的内边距、面板标题行与其内容区、页面内面板与面板。

块与块之间用容器自己的 `gap` 表达，不用 `mt-*` 给单个块补外边距——同一容器里混用两种来源，间距就会随内容增减而漂移。

图标与图标按钮的盒尺寸使用 `--ui-glyph-sm|md|lg`（16/20/24），不从间距阶梯借用。标准边界使用 `--border-width-default`。布局宽度、Header 高度和内容行宽使用对应 `--layout-*`、`--header-height` 与 `--content-*` token。仅两例光学偏移（按下位移、附件删除盒与 chip 的负叠）刻意留在网格外，并在规则内标 `geometry-exempt`。

控件内的图标尺寸由 CSS 拥有：`.prelude-button__content`、`.field-action`、`.row-action`、`.prelude-dialog__close`、`.prelude-toast__close` 与 `.prelude-toast [data-icon]` 下的 `svg` 取 `--ui-glyph-sm`。调用点不写 `size={n}`：SVG 的 `width` 表现属性优先级低于 CSS，写了不会生效。

会话行的置顶角标是脱离控件的装饰图形，没有 CSS 归属，尺寸由调用点的 `size` 决定。

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

阶梯同时以原子类暴露：`text-xs|sm|meta|md|lg|xl|2xl`、`leading-solid|display|tight|heading|compact|base|relaxed|copy`、`font-regular|medium|semibold`，全部指向上述 token，不允许写死数值。

标题、指标、标签等常见配对固定为七个语义角色，页面选择角色而不是临时拼字号与行高：

| 角色 | 用途 | 组合 |
| --- | --- | --- |
| `type-eyebrow` | 标题上方的引导标签 | serif `xs` / medium / `tight` / tertiary |
| `type-metric` | 大号数字指标 | serif `xl` / medium / `display` / primary |
| `type-hero` | 页面级响应式大标题（认证、终态页、面试空态） | serif `clamp(xl, 5vw, 2xl)` / medium / `display` / primary |
| `type-title` | 区块主标题 | serif `lg` / medium / `tight` / primary |
| `type-subtitle` | 次级标题 | serif `md` / medium / `compact` / primary |
| `type-label` | 字段与条目名称 | serif `sm` / medium / `compact` / secondary |
| `type-body` | 成段正文 | sans `sm` / regular / `relaxed` / secondary |
| `type-meta` | 计数、时间、辅助说明 | sans `xs` / regular / `compact` / tertiary |

行高由字号决定：同一字号只对应一种行高。角色未覆盖的配对（如报告内联分数）用原子类显式组合，不新增角色。

标题角色与 DOM 层级一一对应：`h1` 用 `type-hero`（页面主标题）或 `workspace-header__title`，`h2` 用 `type-title`，`h3` 用 `type-subtitle`，字段与条目名用 `type-label`。`Panel` 按 `level` 选出 `h2`/`h3` 与对应角色，标题层级由组件决定。

排版取值以**实际渲染值**为准，声明意图与被覆盖的历史写法不作为依据。

### Motion

动效使用 `--motion-duration-*` 与 `--motion-ease-standard`。颜色与表面变化使用 token transition；进入和退出优先 opacity 与 transform。几何动画采用 `transform`，加载态保持控件尺寸稳定，并支持 `prefers-reduced-motion`。

### Style Assembly

界面样式只有两种写法：调用点的 Tailwind 原子类，和 `frontend/src/shared/styles/index.css` 中具名注册的 `@utility`。feature 目录不含 CSS 文件，`features/*` 与 `app/shell` 只引入 token 与原子类。

- 原子类优先。能被 `flex`、`gap-md`、`bg-surface`、`rounded-lg` 表达的，不注册新 utility。
- 只有当组合无法用原子表达时才注册：嵌入 `var()` 的多值简写、`auto-fit`/`minmax` 轨道、来自 token 的 logical border、跨元素状态传播、`@keyframes`。
- 只组合通用 token 的 utility 取通用名（`nav-item`、`list-row`、`elevated-modal`）；写死某个 owner 布局 token 的必须带该 owner 前缀（`sidebar-pane`、`prompt-bar-input`、`report-columns`），让耦合可见。
- 跨组件状态用 Tailwind `group/*`、`peer/*` 与 `data-*` 变体表达，不用后代 BEM 选择器；文档级行为（报告打印）留在 `index.css` 的 `@media print`，以 `data-slot` 为锚点。
- 层叠可依赖的只有一条：未分层类 > utilities 层 > `@layer base`。同一元素上叠加「注册 utility + 核心原子」或「utility + 未分层类」时，谁生效由 Tailwind 内部排序决定、**无法从源码顺序推导**，因此视为缺陷：调用点不得用核心原子去改 utility 已声明的属性。
- 需要覆盖时只有两种写法：为基座声明一个 `base-variant` 命名的变体 utility（唯一被允许的覆盖），或把该属性从基座拆出去交给调用点独占。冲突由 `npm run verify:cascade` 用构建产物实测，不靠约定自觉。
- 未分层类同样会静默压掉同一元素上的核心原子：结果可预测，但调用点写下的原子是死代码（图形尺寸被页面类钉死就是这么来的）。因此未分层页面类不声明 `size`/`padding`/`margin` 这类调用点可能要覆写的几何；`verify:cascade` 的 dead-atom 检查会报出这种组合。
- 元素级重置（`button`、`a`、标题与列表 margin）必须写在 `@layer base` 内；未分层的元素选择器会压过整个 utilities 层，使组件无法声明自己的文字颜色与间距。

## Components

Base UI 提供 Dialog、Popover、Menu、Tooltip、Combobox、Select、Focus 与 Keyboard 行为。Prelude-owned source 负责视觉、语义 variant 和组合接口。每类交互对应一套 primitive。

### Actions

Button variant 明确表达主操作、次操作、轮廓、轻操作和破坏性操作。图标操作使用熟悉图标与可访问名称。loading 状态保留原始宽高，文本和图标使用透明度或受控替换。

按钮高度全项目一档（`--ui-height-control`）。`size` 区分盒形状：`default` 为文本按钮，`icon` 为等边图标按钮。`shape` 表达不改变高度的内容形态：`action` 为固定不收缩的宽动作，`hold` 为按住说话。

### Fields

Input、Textarea、Select 与 Combobox 使用 `--color-surface` 表面、`--color-border-warm` 边界、token padding 与衬线字体。字段焦点改变现有边界颜色，错误状态使用 `--color-error`。

### Floating Surfaces

Dropdown、Select 与 Combobox 使用 `--color-surface`、`--color-text-primary`、`--color-border-warm`、`--radius-md` 和 `--shadow-whisper`。这一中性高对比表面保持文字清晰，也与品牌强调色分离。

Tooltip 反过来用文字色做底、表面色做字（`--color-text-primary` / `--color-surface`），配 `--radius-sm` 与 `--shadow-whisper`：它是贴在控件旁的一行短说明，反色让它不与它所注解的表面混淆。

Tooltip 内容使用 `--font-size-xs`、token padding 和 `--content-tooltip-max-inline-size`。primitive 统一 trigger 间距和 opacity 动效。截断文字的定位锚点是完整交互控件。

Dialog、Confirm 与 Toast 使用同一表面语义；遮罩使用 `--mask-overlay`，Dialog 使用 `--shadow-modal`。`.prelude-dialog` 自带 `--spacing-xl` 内边距，是可直接放内容的浮层；`--workspace` 变体是 full-bleed 壳层，内边距与分区由调用点拥有（设置面板即此形态）。Confirm 复用 `.prelude-dialog` 的 chrome，只覆写自身宽度（`--layout-confirm-max-inline-size`）与动作行——动作按钮等分铺满整行，不缩在右侧。

### Composition

`shared/ui/panel.tsx` 的 `Panel` 是「带标题的表面」的唯一实现：标题行同时拥有标题、副标题与右侧操作区，操作按钮与标题同行、由 flex 竖直居中——浮层的右上角不使用绝对定位，位置不随标题字号或内边距漂移。

- `layout="fill"` 用于铺满容器的高度型表面（设置弹窗的五个分区、工作台浮层）：标题行带下边界，内容区自带 `--spacing-lg` 内边距并独立滚动。
- `layout="card"` 用于随内容增高的自足块（组件实验台的每个面板、岗位管理的两块面板）：自带边界、圆角、`--spacing-lg` 内边距与 `elevated-whisper`，标题行与内容区之间同样是 `--spacing-lg`。
- `level` 决定标题用 `h2` 还是 `h3`，并随之选择 `type-title` 或 `type-subtitle`；`eyebrow` 是标题上方的 `type-eyebrow` 引导标签，`description` 是标题下方的 `type-meta` 说明，`actions` 是右侧操作区，`footer` 是带上下边界的底部动作条。

重复条目行用 `list-row`（带边界的完整行）或 `row-label-end`（名称与尾部动作两端对齐），加载、空库与失败统一落到 `empty-state`。页面区段仍用无框布局与受控内容宽度，只有需要明确边界的数据对象才升级为 `card`。

## Accessibility

- 交互控件具备可访问名称与完整键盘路径。
- 键盘焦点使用可见的语义边界；字段、动作、选中与打开状态彼此独立。
- 图标装饰使用空替代文本；信息图像提供等价文本。
- 文本与交互目标在桌面布局中保持互不遮挡。
- 系统高对比度与 reduced motion 偏好保持可用。

## Source Adoption

shadcn 提供 Button、Field 与表单控件的源码组织，Base UI 提供浮层交互语义。[Beautiful UI](https://www.beautifului.dev/) Prompt Bar 组合用于面试输入区。品牌字体由 Fontsource 本地可变字体资产提供，运行时不依赖远端字体服务。组件视觉统一由本文件和 Prelude Design Tokens 定义。

## Validation

UI 改动验证命令以 `docs/setup.md#验证` 与 `docs/quality/ui-quality-system.md` 为准。

视觉审查以本文件和 `frontend/src/shared/styles/index.css` 的 token 定义为准。
