# UI 设计规范

> 当前版本基线。只保留已落地且需要继续遵守的规则。

## 1. 视觉基线

| 项 | 约束 |
| --- | --- |
| 背景 | 暖灰纸感背景，禁止纯白页面背景 |
| 主色 | `#9e7b6a` 及其低饱和暖灰派生色 |
| 表面 | `#faf9f5` / `#f5f4ed` |
| 表面悬停 | `--color-surface-hover`（6% 品牌色混入 surface） |
| 表面次级 | `--color-surface-muted`（12% 品牌色混入 surface） |
| 边框 | 低饱和暖灰线，不使用高对比黑线 |
| 阴影 | 只允许轻阴影和 1px ring，禁止重投影 |
| 圆角 | 卡片 16-24px，按钮和输入框不低于 8px |
| 动效 | 只保留轻量 `opacity / translate`，禁止夸张缩放和弹簧动效 |

## 2. 字体层级

| 角色 | 字体 | 约束 |
| --- | --- | --- |
| 页面标题 / 卡片标题 | `--font-serif` | 字重不超过 500 |
| 正文 / 表单 / 按钮 / 菜单 | `--font-sans` | 保持 15-20px，行高不低于 1.4 |
| 代码 / 技术片段 | `--font-mono` | 仅用于代码或接口片段 |

禁止在按钮、输入框、Badge 中使用 serif 字体。

## 3. 全局导航

- 登录后业务页统一使用左侧固定侧边栏导航，不再使用顶部粘性 Header。
- 侧边栏宽度展开时 260px，折叠后收为 68px icon rail，通过顶部折叠按钮切换。
- 顶部区域：`BrandMetaballs` Logo + 品牌名 `Prelude`，右侧放折叠/展开按钮。
- `开始新面试` 主操作按钮固定在品牌区正下方。
- 中段为会话列表，分「进行中」与「已完成」两组，支持置顶和删除操作。
- 底部导航固定三项：简历管理、数据看板、设置（含 LLM 配置和用户设置子项）。
- 折叠状态下只保留图标，隐藏所有文字标签，当前活跃会话用高亮点标记。
- 各业务页自带独立 `workspace-header`（sticky，高度 72px），显示页面标题和页面级操作。
- 登录页不显示侧边栏。

## 4. 登录 / 注册页

- 登录页不显示全局 Header。
- 登录/注册使用同一张卡片，切换时卡片外尺寸不变化。
- 卡片宽度按当前实现保持 `min(100%, 1180px)`，`transform: scale(1)`。
- 左栏只包含大号 `BrandMetaballs` Logo 和一行小字 `AI Mock Interview`。
- 右栏保留 eyebrow、主标题、表单和底部按钮组；删除额外解释性提示词。
- 登录态为注册邮箱输入预留同高空位，避免切换抽动。
- 登录/注册切换按钮放在表单底部，与提交按钮同组。

## 5. 页面与卡片

- 所有业务页使用统一的 `.page__hero`、`.panel__head`、`.panel__actions` 节奏。
- 页面级说明只保留一条必要说明；卡片内不重复解释页面结构。
- 同一视觉层级的卡片必须统一内边距、标题区高度、Badge 尺寸和按钮尺寸。
- 主工作台按上下分段组织：上段为准备与实时面试，下段为历史与报告。
- 卡片内冗余空白优先通过重排信息密度解决，不靠强行拉高卡片。
- `.panel .el-card__body` 使用 `gap: var(--spacing-md); padding: var(--spacing-md)`（16px），禁止使用 `--spacing-lg`。
- `.panel__head` 无 `min-height`，通过 `padding: var(--spacing-md) 0` 自然撑开。
- `.panel__title` 字号 20px，行高 1.4；`.panel__title--small` 字号 18px。
- `.panel__lead` 字号 13px，颜色 `var(--color-text-tertiary)`。
- 嵌套在 `.panel` 内部的 `.detail-card` 使用 `padding: var(--spacing-sm)`（8px），避免双重内边距。
- `.form-grid` 使用 `gap: var(--spacing-sm)`（8px），表单项形成紧凑分组。
- `.button-row` 使用 `margin-top: var(--spacing-md)`（16px），紧跟配置项。

## 6. 按钮与 Badge

- 主操作只放在所属模块内，禁止跨模块按钮散落在页面 hero 或正文中。
- 次操作与主操作同组排列，不另起孤立按钮区。
- 历史会话中的状态 Badge 与回放按钮保持同一侧栏节奏，尺寸一致。
- Badge 使用浅沙底、暖灰文字、小圆角胶囊，不使用高饱和状态色。
- 所有按钮禁止使用 Element Plus 的 `size` 属性（`large` / `small`），统一使用 `ui-button--compact` class 控制尺寸。
- 同一页面的主操作按钮与次操作按钮必须使用相同的 `ui-button--compact` 尺寸。

## 7. 表单与输入

- 输入框使用浅表面背景、暖灰边框和 8px 以上圆角。
- 文件上传使用封装上传行：左侧选择按钮，右侧文件名，禁止暴露原生 file input 大块样式。
- 设置页只保留当前模块保存按钮，不放返回主工作台、数据看板等跨页面入口。
- 所有 `ElSelect` 和 `ElInput` 禁止使用 `size` 属性，高度由 `--ui-height-sm` Token 统一控制。

## 8. 对话、回放与报告

- `system` 消息作为系统说明块，不渲染为"我"的回答。
- `user` 消息靠右，`assistant / 面试官` 消息靠左。
- Demo 模拟面试内容应回答面试官问题，避免回答"系统消息"。
- Markdown 报告使用卡片化阅读面板，禁止退回纯 `<pre>` 文本面板。
- 聊天气泡 `max-width: min(80%, 760px)`，防止超宽屏下文本拉伸过大。
- 消息列表首次挂载和会话切换时必须自动滚动到底部，使用 `nextTick` + `requestAnimationFrame` 双重保险。

## 9. 图表与数据看板

- 图表必须放在标准卡片内。
- 图表色彩读取现有暖灰/品牌 token，不新增高饱和彩色主题。
- 无数据时显示空态，不渲染空图。

## 10. 侧边栏

- 所有组件高度统一为 32px（`app-sidebar__btn`、`session-item-btn`、`settings-dropdown__item`）。
- 组件间距统一为 12px（margin / gap / padding），例外：会话操作按钮（置顶/删除）内部间距保持 4px。
- 会话列表使用 `flex + gap` 控制间距，按钮使用 `min-height / max-height / line-height: 1` 锁定高度，防止 flex 的 `min-height: auto` 撑高。
- LLM 配置图标使用终端样式 `>_`（polyline 4 17 / 10 11 / 4 5 + 底部光标线）。
- 折叠态按钮使用 `width: 32px; margin: 0 6px; padding: 0 6px` 数值补偿居中，禁止使用 `justify-content: center` 或 `margin: 0 auto`（离散属性不可动画，会导致图标横跳）。
- 侧边栏滚动条继承全局 `.scrollable` 样式，禁止在 scoped CSS 中重复定义 `::-webkit-scrollbar`。
- `WorkspaceHeader.vue` 禁止在 scoped CSS 中定义 `.workspace-header`，必须继承全局骨架样式以保证 40px 水平对齐。

## 11. 下拉弹层

- `ElDropdown` 和 `ElSelect` 必须通过 `popper-class` 指定自定义弹层 class，禁止使用 Element Plus 默认弹层样式。
- `ElDropdown` 使用 `popper-class="custom-dropdown-popper"`，配套 CSS 定义在 `index.css`。
- `ElSelect` 使用 `popper-class="custom-select-popper"`，配套 CSS 定义在 `index.css`。
- 所有 `ElSelect` 必须同时添加 `fit-input-width` 属性，强制弹层宽度与触发器等宽。
- 弹层宽度必须严格等于触发器宽度，通过 `usePopperMatchTrigger` composable 实现：`ResizeObserver` 测量触发器 `getBoundingClientRect()`，输出 `popperStyle` 绑定到 `:popper-style`。
- 弹层边框禁止使用真实 `border`（会占布局空间，与触发器 `box-shadow: inset` 内嵌边框错位 1px），改用 `border: none` + `box-shadow: inset 0 0 0 1px var(--color-border-warm)` 模拟。
- 弹层 `padding: 1px`，使内容盒精确落在内嵌边框内侧。
- 弹层 `will-change: transform` + `backface-visibility: hidden`，强制 GPU 合成层，稳定子像素渲染。
- 菜单项高度通过 CSS 变量 `var(--trigger-height, fallback)` 继承触发器实测高度，`line-height: 1` + `display: flex; align-items: center`。
- 菜单项文本过长时 `white-space: nowrap; overflow: hidden; text-overflow: ellipsis` 截断。
- 新增弹层时复用 `usePopperMatchTrigger` composable + 对应 popper-class，零成本接入。

## 12. 通知

- 所有页面级通知（成功、警告、错误）统一使用 `usePageNotice` composable 的 `showNotice()` 方法。
- 禁止直接调用 `ElMessage.success()` / `ElMessage.warning()` / `ElMessage.error()`，这些会使用 Element Plus 默认样式而非项目统一的 `page-notice` 样式。

## 13. 可访问性

- `--color-text-tertiary` 对比度必须满足 WCAG AA 4.5:1（当前值 `#6b6a65`，在 `#faf9f5` 上约 4.6:1）。
- 所有交互元素（按钮、列表项、下拉项）必须定义 `focus-visible` 样式：`outline: 2px solid var(--color-focus); outline-offset: -2px`。

## 14. Token 体系

### Color Token (光学派生体系)

全局交互反馈色必须使用以下基于主色的光学派生 Token，严禁硬编码灰色：

| Token | 定义 | 用途 |
|-------|-----|------|
| `--color-surface-hover` | `color-mix(brand 6%, surface)` | 极净暖灰，用于所有交互组件的 Hover 态背景 |
| `--color-surface-muted` | `color-mix(brand 12%, surface)` | 浅卡其暖灰，用于 Active 态、选中态或次级面板底色 |

### Spacing Token

所有间距必须使用以下变量，禁止硬编码 `px`：

| Token | 值 | 用途 |
|-------|-----|------|
| `--spacing-xs` | 4px | 极小间距（图标与文字、badge 内边距） |
| `--spacing-sm` | 8px | 小间距（按钮组 gap、表单 gap、嵌套卡片 padding） |
| `--spacing-md` | 16px | 中间距（面板 gap/padding、grid gap、按钮行 margin-top） |
| `--spacing-lg` | 24px | 大间距（section gap、header 垂直 padding） |
| `--spacing-xl` | 32px | 特大间距（保留） |
| `--spacing-2xl` | 40px | 页面内容区水平 padding（Header + Content 严格对齐） |

### Height Token

所有组件高度必须使用以下变量，禁止硬编码 `px`：

| Token | 值 | 用途 |
|-------|-----|------|
| `--ui-height-base` | 42px | 标准按钮 |
| `--ui-height-sm` | 34px | 紧凑按钮、输入框、下拉框 wrapper |
| `--header-height` | 72px | 工作区页头 |
| `--composer-height` | 260px | 底部输入框占位高度 |

### 工作区骨架

`.workspace-page`、`.workspace-header`、`.workspace-page__content`、`.page-grid`、`.detail-grid`、`.field-grid`、`.detail-card`、`.button-row` 全部定义在 `index.css`，禁止在 Vue 文件 scoped 样式中重复定义。

`.workspace-header` 和 `.workspace-page__content` 的水平 padding 统一为 `var(--spacing-2xl)`（40px），保证 Header 与 Content 严格垂直对齐。

## 15. 禁止项

- 禁止新增页面级跨路由按钮。
- 禁止重复解释布局设计意图的提示词。
- 禁止同模块组件出现不同按钮尺寸、Badge 尺寸或卡片边距。
- 禁止冷色 SaaS 风、深色终端风和高饱和强调色。
- 禁止为一次性页面效果增加新设计体系。
- 禁止使用 Element Plus 的 `size` 属性控制按钮/输入框尺寸。
- 禁止 `ElDropdown` / `ElSelect` 不加 `popper-class` 使用默认弹层样式。
- 禁止直接调用 `ElMessage`，统一走 `usePageNotice`。
- 禁止交互元素缺少 `focus-visible` 样式。
- 禁止在间距和组件高度上使用硬编码 `px`，统一使用 `--spacing-*` 和 `--ui-height-*` Token。
- 禁止在 Vue scoped 样式中重复定义全局骨架类（`.workspace-*`、`.page-grid`、`.detail-*`、`.field-grid`、`.button-row`）。
- 禁止在侧边栏 scoped CSS 中定义 `::-webkit-scrollbar`，继承全局 `.scrollable`。
- 禁止在 `WorkspaceHeader.vue` scoped CSS 中定义 `.workspace-header`，必须继承全局骨架。
- 禁止侧边栏折叠态使用 `justify-content: center` 或 `margin: auto`（离散属性不可动画）。
- 禁止在 Vue scoped CSS 中使用原生 `white`、`black`、`#hex` 颜色值。统一使用 `var(--color-*)` Token 体系。
- 绝对禁止使用 `color-mix(in srgb, ... black)` 混入纯黑来制造背景加深效果（会导致"脏灰"），加深必须基于 `--color-surface-hover/muted`。
- 禁止 `height: auto` 参与过渡动画（不可补间），改用 `max-height` 技巧。
- 禁止使用 `--color-sand` 作为 hover 背景色，统一使用 `--color-surface-hover`。`--color-sand` 仅用于边框和深色占位 fallback。
