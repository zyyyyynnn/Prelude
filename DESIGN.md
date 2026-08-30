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
- `--color-border`、`--color-border-warm`：边界。
- `--color-focus-field`、`--color-focus-action`：字段与动作焦点。
- `--color-error`：错误与破坏性动作。

组件颜色使用 CSS var、Tailwind token utility 或 `color-mix()`。基础色值集中在 token 定义中。浅色与暗色模式通过同一语义 token 映射。

### Spacing And Size

间距使用 `--spacing-xs` 至 `--spacing-2xl` 阶梯。基础控件高度为 `--ui-height-base`，紧凑控件高度为 `--ui-height-compact`。布局宽度、Header 高度和内容行宽使用对应 `--layout-*`、`--header-height` 与 `--content-*` token。

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
- 字号使用 `--font-size-xs` 至 `--font-size-2xl` 阶梯，组件内部采用紧凑标题尺度。

### Motion

动效使用 `--motion-duration-*` 与 `--motion-ease-standard`。颜色与表面变化使用 token transition；进入和退出优先 opacity 与 transform。几何动画采用 `transform`，加载态保持控件尺寸稳定，并支持 `prefers-reduced-motion`。

## Components

Base UI 提供 Dialog、Popover、Menu、Tooltip、Combobox、Select、Focus 与 Keyboard 行为。Prelude-owned source 负责视觉、语义 variant 和组合接口。每类交互对应一套 primitive。

### Actions

Button variant 明确表达主操作、次操作、轮廓、轻操作和破坏性操作。图标操作使用熟悉图标与可访问名称。loading 状态保留原始宽高，文本和图标使用透明度或受控替换。

### Fields

Input、Textarea、Select 与 Combobox 使用 `--color-surface` 表面、`--color-border-warm` 边界、token padding 与衬线字体。字段焦点改变现有边界颜色，错误状态使用 `--color-error`。

### Floating Surfaces

Dropdown、Select、Combobox 与 Tooltip 使用 `--color-surface`、`--color-text-primary`、`--color-border-warm`、`--radius-md` 和 `--shadow-whisper`。这一中性高对比表面保持文字清晰，也与品牌强调色分离。

Tooltip 内容使用 `--font-size-xs`、token padding 和 `--content-tooltip-max-inline-size`。primitive 统一 trigger 间距和 opacity 动效。截断文字的定位锚点是完整交互控件。

Dialog、Confirm 与 Toast 使用同一表面语义；遮罩使用 `--mask-overlay`，Dialog 使用 `--shadow-modal`。

### Composition

页面区段使用无框布局与受控内容宽度。Card 用于重复项目、独立工具或需要明确边界的数据对象。组件通过明确 variant 和 composition 表达差异。

## Accessibility

- 交互控件具备可访问名称与完整键盘路径。
- 键盘焦点使用可见的语义边界；字段、动作、选中与打开状态彼此独立。
- 图标装饰使用空替代文本；信息图像提供等价文本。
- 文本与交互目标在桌面布局中保持互不遮挡。
- 系统高对比度与 reduced motion 偏好保持可用。

## Source Adoption

shadcn 提供 Button、Field 与表单控件的源码组织，Base UI 提供浮层交互语义。[Beautiful UI](https://www.beautifului.dev/) Prompt Bar 组合用于面试输入区。组件视觉统一由本文件和 Prelude Design Tokens 定义。

## Validation

UI 改动执行：

```powershell
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:a11y
npm --prefix frontend run verify:visual
npm --prefix frontend run build
npm --prefix frontend run test:smoke
```

视觉审查以本文件和 `frontend/src/shared/styles/index.css` 的 token 定义为准。
