# UI 设计规范

> 当前版本基线。只保留已落地且需要继续遵守的规则。

## 1. 设计原则

- 暖色调纸感：背景 `#f5f4ed` / 表面 `#faf9f5`，禁止纯白页面背景
- 主色体系：`#9e7b6a` 及其低饱和暖灰派生色，禁止冷色 SaaS 风和高饱和强调色
- 轻量动效：只保留 opacity / transform / background-color 过渡，禁止夸张缩放和弹簧动效
- Token 驱动：间距、高度、颜色全部使用 CSS 变量，禁止硬编码 px / hex / white / black
- 组件一致性：同一视觉层级的卡片必须统一内边距、标题区高度、Badge 尺寸和按钮尺寸

## 2. 色彩与 Token 体系

### 2.1 色彩体系

| Token | 值 | 用途 |
|-------|-----|------|
| `--color-text-primary` | `#141413` | 基础文本色 |
| `--color-text-secondary` | `#5e5d59` | 次级文本色 |
| `--color-text-tertiary` | `#6b6a65` | 第三级辅助文本色（WCAG AA 4.5:1） |
| `--color-brand` | `#9e7b6a` | 品牌主色 |
| `--color-brand-light` | `color-mix(brand 12%, surface)` | 品牌暗示高亮（侧边栏 Active 态） |
| `--color-surface` | `#faf9f5` | 标准表面 |
| `--color-surface-hover` | `color-mix(#2C2A29 4%, surface)` | Hover 态背景 |
| `--color-surface-muted` | `color-mix(#2C2A29 8%, surface)` | Active 态、次级按钮底色 |
| `--color-sand` | `#e8e6dc` | 弱底色填充（如 Badge） |
| `--color-border` | `#f0eee6` | 标准边框色 |
| `--color-border-warm` | `#e8e6dc` | 暖灰次级边框色 |
| `--color-ring` | `#d1cfc5` | 焦点/选中环 |
| `--color-ring-deep` | `#c2c0b6` | 深环色 |
| `--color-line-decor` | `#c8c6be` | 装饰线（如登录卡片外框）|
| `--color-error` | `#b53333` | 错误/告警 |
| `--color-focus` | `#b39b8d` | 键盘/输入框焦点环（暖色） |
| `--color-bg` | `#f5f4ed` | 页面全局背景（纸感暖灰） |
| `--color-text-button` | `#4d4c48` | 按钮默认文字色 |
| `--color-coral` | `#b08878` | 暖色警告/强调（映射 Element Plus warning） |
| `--color-line-decor-light` | `#dddbd3` | 浅装饰线（如 SVG 描边） |

### 2.2 Spacing Token

所有间距必须使用以下变量，禁止硬编码 `px`：

| Token | 值 | 用途 |
|-------|-----|------|
| `--spacing-xs` | 4px | 极小间距（图标与文字、badge 内边距） |
| `--spacing-sm` | 8px | 小间距（按钮组 gap、表单 gap、嵌套卡片 padding） |
| `--spacing-md` | 16px | 中间距（面板 gap/padding、grid gap、按钮行 margin-top） |
| `--spacing-lg` | 24px | 大间距（section gap、header 垂直 padding） |
| `--spacing-xl` | 32px | 特大间距（保留） |
| `--spacing-2xl` | 40px | 页面内容区水平 padding（Header + Content 严格对齐） |

### 2.3 Height Token

所有组件高度必须使用以下变量，禁止硬编码 `px`：

| Token | 值 | 用途 |
|-------|-----|------|
| `--ui-height-base` | 42px | 标准按钮 |
| `--ui-height-md` | 36px | 侧边栏按钮/会话项/菜单项统一高度 |
| `--ui-height-sm` | 34px | 紧凑按钮、输入框、下拉框 wrapper |
| `--header-height` | 72px | 工作区页头 |
| `--composer-height` | 260px | 底部输入框占位高度 |
| `h-10` | 40px | 新增基准声明，作为所有核心交互组件（Button/Input/SelectTrigger/MenuItem）的不可动摇的统一高度 |

## 3. 字体层级

| 角色 | 字体 | 约束 |
| --- | --- | --- |
| 页面标题 / 卡片标题 / 表单 Label | `--font-serif` | 字重不超过 500 |
| 正文 / 按钮 / 菜单 | `--font-sans` | 保持 15-20px，行高不低于 1.4 |
| 代码 / 技术片段 | `--font-mono` | 仅用于代码或接口片段 |

禁止在输入框、Badge 中使用 serif 字体。品牌主操作按钮（如"开始新面试"）可使用 serif 以强化品牌调性，字重不超过 500。

## 4. 布局与导航

### 4.1 全局导航

- 登录后业务页统一使用左侧固定侧边栏导航
- 各业务页自带独立 `.workspace-header`（sticky，高度 `--header-height`）
- 登录页不显示侧边栏

### 4.2 侧边栏

- 展开宽度 260px，折叠态 52px（`calc(var(--ui-height-md) + var(--spacing-sm) * 2)`）
- 所有组件高度统一 `var(--ui-height-md)` (36px)
- 间距全面复用 `var(--spacing-sm)` (8px)
- 折叠态数学模型：`padding: 0 var(--spacing-sm); margin: 0; justify-content: flex-start;`，严禁 `auto` 或 `center`
- 滚动条继承全局 `.scrollable`，禁止在 scoped CSS 中重复定义 `::-webkit-scrollbar`
- `WorkspaceHeader.vue` 禁止在 scoped CSS 中定义 `.workspace-header`

### 4.3 弹窗

- 全局弹窗必须设置 `max-height: 70vh` + `overflow-y: auto; flex: 1; min-height: 0`

### 4.4 工作区骨架

- `.workspace-page`、`.workspace-header`、`.workspace-page__content`、`.page-grid`、`.detail-grid`、`.field-grid`、`.detail-card`、`.button-row` 全部定义在 `index.css`，禁止在 Vue scoped 样式中重复定义
- `.workspace-header` 和 `.workspace-page__content` 水平 padding 统一 `var(--spacing-2xl)` (40px)

## 5. 组件规范

### 5.1 卡片与面板

- 页面级说明只保留一条必要说明；卡片内不重复解释页面结构
- `.panel .el-card__body` 使用 `gap: var(--spacing-md); padding: var(--spacing-md)`，禁止 `--spacing-lg`
- `.panel__head` 无 `min-height`，通过 `padding: var(--spacing-md) 0` 自然撑开
- `.panel__title` 字号 20px，行高 1.4；`.panel__title--small` 字号 18px
- `.panel__lead` 字号 13px，颜色 `var(--color-text-tertiary)`
- 嵌套 `.detail-card` 使用 `padding: var(--spacing-sm)` (8px)
- `.form-grid` 使用 `gap: var(--spacing-sm)` (8px)
- `.button-row` 使用 `margin-top: var(--spacing-md)` (16px)

### 5.2 按钮与 Badge

- 主操作只放在所属模块内，禁止跨模块按钮散落
- Badge 使用浅沙底、暖灰文字、小圆角胶囊
- 所有按钮禁止使用 Element Plus `size` 属性，统一 `.ui-button--compact`
- 按钮 Hover 态只允许加深 `background-color`，严禁改变 `border-color` 或增加 `box-shadow`

### 5.3 表单与输入

- 输入框使用浅表面背景、暖灰边框和 8px 以上圆角
- 所有 `ElSelect` 和 `ElInput` 禁止使用 `size` 属性，高度由 `--ui-height-sm` 控制
- 文件上传使用封装上传行，禁止暴露原生 file input

### 5.4 下拉弹层重写

- Content Z-index 必须为 `z-[105]`（碾压 Dialog 的 101）。
- Content 禁止裸写 `border`，必须配对 `border-border` 防止 currentColor 纯黑回退。
- Content 取消 `p-1` 内边距，使用 Viewport 变量实现 100% 宽度等比对齐。
- Menu Items 必须强制加上 `h-10` 和 `rounded-md`，严禁悬浮时出现贴边直角。
- 菜单项文本过长时 ellipsis 截断

### 5.5 通知

- 所有页面级通知统一使用 `usePageNotice`
- 禁止直接调用 `ElMessage`，避免样式不统一

## 6. 交互规范

### 6.1 动效与过渡

- 过渡时长统一 0.14s - 0.2s ease，仅用于 `opacity`、`transform`、`background-color`
- 禁止为了视觉刺激滥用缩放或弹簧物理动效
- 禁止 `height: auto` 参与过渡动画（不可补间），改用 `max-height` 技巧

### 6.2 滚动策略

- 在有动态展开内容的页面，默认维持 `scrollbar-gutter: stable` 占位

### 6.3 对话与报告

- `system` 消息作为系统说明块，`user` 靠右，`assistant` 靠左
- Markdown 报告使用卡片化阅读面板，禁止纯 `<pre>`
- 聊天气泡 `max-width: min(80%, 760px)`
- 消息列表首次挂载和会话切换时自动滚动到底部

### 6.4 图表

- 图表必须放在标准卡片内
- 图表色彩读取现有暖灰/品牌 token，不新增高饱和彩色主题
- 无数据时显示空态，不渲染空图

## 7. 可访问性

- `--color-text-tertiary` 对比度必须满足 WCAG AA 4.5:1
- 所有交互元素必须定义 `focus-visible` 样式

## 8. 禁止项

- 禁止新增页面级跨路由按钮
- 禁止冷色 SaaS 风、深色终端风和高饱和强调色
- 禁止为一次性页面效果增加新设计体系
- 禁止交互元素缺少 `focus-visible` 样式
- 禁止在 Vue scoped CSS 中使用原生 `white`、`black`、`#hex` 颜色值
- 禁止使用 `color-mix(in srgb, ... black)` 混入纯黑制造背景加深
- 禁止使用 `--color-sand` 作为 hover 背景色，统一使用 `--color-surface-hover`
- 禁止侧边栏折叠态使用 `justify-content: center` 或 `margin: auto`
- 禁止在侧边栏 scoped CSS 中定义 `::-webkit-scrollbar`
- 🔴 禁止裸写 border 而不指定颜色（防黑框）。
- 🔴 禁止在 Focus 环清理时滥用 shadow-none 导致原生 outline 逃逸。
- 🔴 禁止写全局的 [data-state="open"] 粗暴覆盖。
- 🔴 禁止在文档流中使用弹层导致父容器抖动，必须使用 absolute inset-0 z-10 进行 Z 轴覆盖。
