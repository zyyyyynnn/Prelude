# UI 设计规范

> 当前版本基线。只保留已落地且需要继续遵守的规则。
>
> 最后更新：2026-06-04 · 技术栈：Vue 3 + shadcn-vue + Tailwind CSS v4 + reka-ui

---

## 1. 设计原则

- **暖色调纸感**：背景 `#f5f4ed` / 表面 `#faf9f5`，禁止纯白页面背景
- **主色体系**：`#9e7b6a` 及其低饱和暖灰派生色，禁止冷色 SaaS 风和高饱和强调色
- **GPU 动效**：优先过渡 `opacity` 和 `transform`。Layout 属性（`height`、`width`、`max-height`、`grid-template-rows`）原则上禁止参与动画，仅限已批准的例外（见 §6.3）
- **Token 驱动**：间距、高度、颜色全部使用 CSS 变量，禁止硬编码 `px` / `hex` / `white` / `black`
- **组件一致性**：同一视觉层级的卡片必须统一内边距、标题区高度、Badge 尺寸和按钮尺寸

---

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
| `--color-line-decor` | `#c8c6be` | 装饰线（如登录卡片外框） |
| `--color-error` | `#b53333` | 错误/告警 |
| `--color-focus` | `#b39b8d` | 键盘/输入框焦点环（暖色） |
| `--color-bg` | `#f5f4ed` | 页面全局背景（纸感暖灰） |
| `--color-text-button` | `#4d4c48` | 按钮默认文字色 |
| `--color-coral` | `#b08878` | 暖色警告/强调 |
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

所有组件高度必须使用以下变量或 Tailwind 等价值，禁止硬编码 `px`：

| Token | 值 | 用途 |
|-------|-----|------|
| `--ui-height-base` | 34px | shadcn 标准交互组件（Button/Input/SelectTrigger/SelectItem/DropdownMenuItem） |
| `--ui-height-md` | 34px | 侧边栏按钮/会话项/菜单项统一高度 |
| `--ui-height-sm` | 34px | 紧凑场景（与 base 统一） |
| `--header-height` | 72px | 工作区页头 |
| `--composer-height` | 260px | 底部输入框占位高度 |

> **shadcn 按钮尺寸变体**（`buttonVariants`）全部锚定 `h-[34px]`：`default`、`sm`、`icon`、`icon-sm` 均为 34px。`lg` 为 `h-11`(44px)，`icon-lg` 为 `size-11`(44px)，仅用于特殊大号场景。

### 2.4 Z-Index 分层

| 层级 | z-index | 用途 |
|------|---------|------|
| `.app-shell__header` | `z-40` | 全局顶栏 |
| `.workspace-header` / `.app-sidebar` | `z-100` | 工作区页头 / 侧边栏 |
| Dialog Overlay + Content | `z-[101]` | 模态弹窗本体 |
| Dropdown / Select / Popover | `z-[105]` | 浮动层，必须高于 Dialog |
| Tooltip | `z-[110]` | 最顶层提示气泡 |

> shadcn 基础组件默认 `z-50` 不够。业务层通过 class 覆盖为上述标准值。

### 2.5 圆角 Token

| Token | 值 | 用途 |
|-------|-----|------|
| `--radius-sm` | 6px | 小圆角（Badge、内部元素） |
| `--radius-md` | 8px | 标准圆角（Button、Input、Select） |
| `--radius-lg` | 12px | 大圆角（卡片内部区块） |
| `--radius-xl` | 16px | 卡片圆角 |
| `--radius-2xl` | 24px | 特大圆角 |
| `--radius-3xl` | 32px | 超大圆角 |

### 2.6 阴影 Token

| Token | 值 | 用途 |
|-------|-----|------|
| `--shadow-ring` | `0 0 0 1px var(--color-ring)` | 轻量轮廓阴影 |
| `--shadow-ring-deep` | `0 0 0 1px var(--color-ring-deep)` | 深轮廓阴影 |
| `--shadow-whisper` | `0 4px 24px rgba(0,0,0,0.05)` | 微弱悬浮阴影 |
| `--shadow-inset` | `inset 0 0 0 1px rgba(0,0,0,0.15)` | 内凹轮廓 |
| `--shadow-modal` | `0 8px 32px rgba(0,0,0,0.12)` | 弹窗阴影 |
| `--mask-overlay` | `rgba(20, 19, 19, 0.38)` | 遮罩层背景 |

---

## 3. 字体层级

| 角色 | 字体 | 约束 |
|------|------|------|
| 页面标题 / 卡片标题 / 表单 Label | `--font-serif`（Lora） | 字重不超过 500 |
| 正文 / 按钮 / 菜单 | `--font-sans`（Inter） | 保持 14-15px，行高不低于 1.4 |
| 代码 / 技术片段 | `--font-mono`（JetBrains Mono） | 仅用于代码或接口片段 |
| 小标签 / Badge（如"面试官"、"2场使用"、"进行中"） | `--font-serif`（Lora） | 允许使用品牌衬线；字重 600（semibold）；字号 12px（text-xs）；字距 `letter-spacing: 0.05em`（tracking-wider），以防止小号衬线粘连 |

**全局字体重置**：`index.css` 中 `body, button, input, select, textarea` 统一设 `font-family: var(--font-sans)`。品牌主操作按钮（如"开始新面试"）可使用 `!font-serif` 强制覆盖以强化品牌调性。小标签 `<Badge>` 组件统一采用品牌衬线，通过字距（tracking-wider）保持精致呼吸感。

---

## 4. 布局与导航

### 4.1 全局导航

- 登录后业务页统一使用左侧固定侧边栏导航
- 各业务页自带独立 `.workspace-header`（sticky，高度 `--header-height`）
- 登录页不显示侧边栏

### 4.2 侧边栏

- 展开宽度 260px，折叠态 50px（`calc(var(--ui-height-md) + var(--spacing-sm) * 2)` = `34 + 8*2`）
- 所有组件高度统一 `var(--ui-height-md)` (34px)
- 间距全面复用 `var(--spacing-sm)` (8px)
- 折叠态数学模型：`padding: 0 var(--spacing-sm); margin: 0; justify-content: flex-start;`，严禁 `auto` 或 `center`
- 滚动条继承全局 `.scrollable`，禁止在 scoped CSS 中重复定义 `::-webkit-scrollbar`
- `WorkspaceHeader.vue` 禁止在 scoped CSS 中定义 `.workspace-header`

### 4.3 弹窗

- 全局弹窗必须设置 `max-height: 70vh` + `overflow-y: auto; flex: 1; min-height: 0`

### 4.4 工作区骨架

- `.workspace-page`、`.workspace-header`、`.workspace-page__content`、`.page-grid`、`.detail-grid`、`.field-grid`、`.detail-card`、`.button-row` 全部定义在 `index.css`，禁止在 Vue scoped 样式中重复定义
- `.workspace-header` 和 `.workspace-page__content` 水平 padding 统一 `var(--spacing-2xl)` (40px)

---

## 5. 组件规范

### 5.1 卡片与面板

- 页面级说明只保留一条必要说明；卡片内不重复解释页面结构
- 面板使用 `gap: var(--spacing-md); padding: var(--spacing-md)`
- 面板标题字号 20px，行高 1.4；小标题字号 18px
- 辅助文字字号 13px，颜色 `var(--color-text-tertiary)`
- 嵌套卡片使用 `padding: var(--spacing-sm)` (8px)
- 表单网格使用 `gap: var(--spacing-sm)` (8px)
- 按钮行使用 `margin-top: var(--spacing-md)` (16px)

### 5.2 按钮与 Badge

- 主操作只放在所属模块内，禁止跨模块按钮散落
- Badge 使用浅沙底、暖灰文字、小圆角胶囊
- shadcn `<Button>` 尺寸变体：`default`(34px)、`sm`(34px)、`icon`(34px)、`icon-sm`(34px)、`lg`(44px)、`icon-lg`(44px)
- 按钮 Hover 态允许加深 `background-color` 和 `transform: translateY(-1px)` 微位移，严禁改变 `border-color` 或增加 `box-shadow`

### 5.3 表单与输入

- `<Input>`、`<SelectTrigger>` 标准高度 `h-[34px]`，padding `px-3 py-1.5`
- `<SelectItem>` 标准高度 `h-[34px]`，padding `pl-8 pr-2`
- `<DropdownMenuItem>` 标准高度 `h-[34px]`，padding `px-2`
- 文件上传使用封装上传行，禁止暴露原生 file input

### 5.4 下拉弹层（shadcn-vue）

- Content z-index 必须为 `z-[105]`（碾压 Dialog 的 `z-[101]`）
- Content 禁止裸写 `border`，必须配对 `border-border` 防止 Tailwind v4 `currentColor` 纯黑回退
- Content 取消 `p-1` 内边距，使用 Viewport 变量实现宽度等比对齐
- Menu Items 必须强制加上 `h-[34px]` 和 `rounded-md`，严禁悬浮时出现贴边直角
- 菜单项文本过长时 ellipsis 截断

### 5.5 Tooltip

- 禁止使用 HTML 原生 `title` 属性，统一使用 shadcn `<Tooltip>` 组件
- `<TooltipContent>` z-index 必须为 `z-[110]`
- `<TooltipContent>` 必须配对 `border-border`（同 5.4 规则）
- 侧边栏 Tooltip 使用 `side="right"` + `:side-offset="8"`，仅在折叠态通过 `v-if="collapsed"` 启用
- 底栏 Tooltip 使用 `side="top"` + `:side-offset="8"`

### 5.6 通知

- 所有页面级通知统一使用 `usePageNotice`
- 禁止直接调用全局消息方法（如 `ElMessage`），统一走 `usePageNotice`

---

## 6. 动效规范（黄金法则）

### 6.1 核心动效基准

| 维度 | 标准值 | 说明 |
|------|--------|------|
| 时长 | `300ms`（`duration-300`） | 全局统一，禁止 150ms/200ms/500ms |
| 曲线 | `ease-in-out` | 全局统一，禁止 `ease`/`ease-out`/`ease-in`/`ease-linear`（语音按钮按下态为唯一批准的 150ms ease-out 特例，见 §6.3） |
| 允许过渡的属性 | 优先 `opacity` 和 `transform` | Layout 属性原则上禁止（`height`/`max-height`/`width`/`grid-template-rows`），仅限 §6.3 批准的例外 |
| 入场隐喻 | `opacity: 0→1` + `translateY(4px→0)` | 从下方 4px 柔和浮入 |
| 离场隐喻 | `opacity: 1→0` + `translateY(0→-4px)` | 向上方 4px 柔和淡出 |

### 6.2 mode-switch 标准（唯一锚点）

所有动画必须与此对齐：

```css
/* 语音/文字切换 — 全站动效唯一锚点 */
.mode-switch-enter-active,
.mode-switch-leave-active {
  transition: opacity 0.3s ease-in-out, transform 0.3s ease-in-out;
}
.mode-switch-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.mode-switch-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
```

### 6.3 各组件动效参数

| 组件 | 时长 | 曲线 | 过渡属性 | GPU 安全 |
|------|------|------|----------|----------|
| 侧边栏宽度 | 300ms | ease-in-out | `width`（Layout 属性，已有 `will-change: width` + `translateZ(0)` 优化） | 🟡 已优化 |
| 侧边栏标签淡入淡出 | 300ms | ease-in-out | `opacity` | ✅ |
| 侧边栏折叠态图标 | 300ms | ease-in-out | `opacity, max-height`（`max-height` 为 Layout 属性） | 🟡 待优化 |
| JD 面板 | 300ms | ease-in-out | `opacity, transform` | ✅ |
| 语音/文字切换 | 300ms | ease-in-out | `opacity, transform` | ✅ |
| 语音按钮按下 | 150ms | ease-out | `transform`（快速响应） | ✅ 特例 |
| 语音按钮释放 | 300ms | ease-in-out | `transform` | ✅ |
| Dialog Overlay | 300ms | ease-in-out | `opacity`（keyframes） | ✅ |
| Dialog Content | 300ms | ease-in-out | `opacity, transform`（keyframes，`translateY(4px)` 微浮动） | ✅ |
| Tooltip | 300ms | ease-in-out | `opacity, transform` | ✅ |
| Dropdown/Select | 300ms | ease-in-out | `opacity, transform` | ✅ |

### 6.4 动效禁区

- 禁止 `transition: all` — 必须精确声明过渡属性（如 `transition: background-color 0.3s ease-in-out`）
- 禁止对 `height`、`max-height`、`width`、`grid-template-rows` 执行过渡动画（除侧边栏宽度外）
- 禁止 `zoom-in-95` / `zoom-out-95` 等缩放形变
- 禁止 `slide-in-from-top-[48%]` 等大幅位移
- 禁止 `height: auto` 参与过渡动画（不可补间）

---

## 7. 可访问性

- `--color-text-tertiary` 对比度必须满足 WCAG AA 4.5:1
- 所有交互元素必须定义 `focus-visible` 样式
- 所有 `<button>` 必须有 `aria-label`（无可见文字时）

---

## 8. 禁止项

### 8.1 色彩与边框

- 禁止在 Vue scoped CSS 中使用原生 `white`、`black`、`#hex` 颜色值
- 禁止使用 `color-mix(in srgb, ... black)` 混入纯黑制造背景加深
- 🔴 禁止裸写 `border` 而不指定颜色 — Tailwind v4 的 `border` 不设 `border-color`，会回退 `currentColor`（纯黑）。必须配对 `border-border` 或 `border-transparent`
- 禁止使用 `--color-sand` 作为 hover 背景色，统一使用 `--color-surface-hover`

### 8.2 动效

- 禁止 `transition: all` — 必须精确声明属性
- 🔴 禁止 `shadow-none` 与 `focus:ring-*` 共存 — `shadow-none` 清零整个 `box-shadow`，导致焦点环失效
- 🔴 禁止写全局的 `[data-state="open"] { outline: none !important }` 粗暴覆盖
- 禁止为了视觉刺激滥用缩放或弹簧物理动效

### 8.3 布局

- 禁止侧边栏折叠态使用 `justify-content: center` 或 `margin: auto`
- 禁止在侧边栏 scoped CSS 中定义 `::-webkit-scrollbar`
- 🔴 禁止在文档流中使用弹层导致父容器抖动 — 覆盖层必须使用 `absolute` + `z-10` 进行 Z 轴覆盖
- 禁止交互元素缺少 `focus-visible` 样式

### 8.4 组件选型

- 禁止使用原生 `title` 属性替代 Tooltip
- 禁止使用 `<DropdownMenu>` 作为表单值选择器 — 表单场景统一用 `<Select>`
- 禁止使用第三方 UI 库的 `size` 属性覆盖组件尺寸，统一使用 shadcn 变体
