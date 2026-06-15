# UI 设计规范

> 当前版本基线。只保留已落地且需要继续遵守的规则。
>
> 最后更新：2026-06-06 · 技术栈：Vue 3 + shadcn-vue + Tailwind CSS v4 + reka-ui

---

## 1. 设计原则

- **暖色调纸感**：背景 `var(--color-bg)` / 表面 `var(--color-surface)`，绝对禁止纯白 `#ffffff` 页面背景。
- **主色体系**：品牌色及其低饱和暖灰派生色，严禁冷色 SaaS 风和高饱和强调色。
- **GPU 动效**：全局禁用 `transition-all`。优先过渡 `opacity` 和 `transform`。Layout 属性（`height`、`width`、`max-height`等）严禁参与动画。
- **Token 驱动**：间距、高度、颜色全面变量化。禁止硬编码 `px`、`#hex`、`white`、`black`。涉及透明度强制使用 `color-mix`。
- **组件一致性**：同一视觉层级的卡片统一内边距、标题区高度、Badge 尺寸和按钮尺寸。

---

## 2. 色彩与 Token 体系

### 2.1 色彩体系 (Base Tokens)

所有色彩禁止使用 `#hex` 和原生 `rgba`，必须使用定义好的变量。透明度叠加时强制使用 `color-mix(in srgb, var(--color-xxx) X%, transparent)`，唯一豁免为 `box-shadow` 内的纯黑/纯白透明度。

| Token | 值 | 用途 |
|-------|-----|------|
| `--color-text-primary` | `#141413` | 基础文本色 |
| `--color-text-secondary` | `#5e5d59` | 次级文本色 |
| `--color-text-tertiary` | `#6b6a65` | 第三级辅助文本色（WCAG AA 4.5:1） |
| `--color-text-button` | `#4d4c48` | 按钮默认文字色 |
| `--color-brand` | `#9e7b6a` | 品牌主色 |
| `--color-brand-light` | `color-mix(in srgb, var(--color-brand) 12%, var(--color-surface))` | 品牌暗示高亮（侧边栏 Active 态） |
| `--color-surface` | `#faf9f5` | 标准表面 (绝不能为纯白) |
| `--color-surface-hover` | `color-mix(in srgb, var(--color-text-primary) 4%, var(--color-surface))` | Hover 态背景 |
| `--color-surface-muted` | `color-mix(in srgb, var(--color-text-primary) 8%, var(--color-surface))` | Active 态、次级按钮底色 |
| `--color-sand` | `#e8e6dc` | 弱底色填充（如 Badge） |
| `--color-border` | `#f0eee6` | 标准边框色 |
| `--color-border-warm` | `#e8e6dc` | 暖灰次级边框色 |
| `--color-ring` | `#d1cfc5` | 焦点/选中环 |
| `--color-ring-deep` | `#c2c0b6` | 深环色 |
| `--color-line-decor` | `#c8c6be` | 装饰线（如登录卡片外框） |
| `--color-line-decor-light` | `#dddbd3` | 浅装饰线（如 SVG 描边） |
| `--color-error` | `#b53333` | 错误/告警 |
| `--color-focus` | `#b39b8d` | 键盘/输入框焦点环（暖色） |
| `--color-coral` | `#b08878` | 暖色警告/强调 |
| `--color-bg` | `#f5f4ed` | 页面全局背景（纸感暖灰） |
| `--mask-overlay` | `color-mix(in srgb, var(--color-text-primary) 38%, transparent)` | 实际的遮罩层背景色定义。对应 shadcn 映射变量为 `--color-mask-overlay`。 |

### 2.2 shadcn-vue 语义映射

通过 `index.css` 将 shadcn 默认颜色体系完美锚定至业务暖色系统：

| shadcn Token | 映射业务 Token | 逻辑说明 |
|--------------|----------------|----------|
| `--background` | `var(--color-bg)` | 全局基底色 |
| `--foreground` | `var(--color-text-primary)` | 默认文本色 |
| `--card` / `--popover` | `var(--color-surface)` | 卡片与弹窗采用表面色 |
| `--primary` | `var(--color-brand)` | 主操作锚定品牌色 |
| `--secondary` / `--muted` | `var(--color-surface-muted)` | 次级操作使用静音色 |
| `--accent` | `var(--color-surface-hover)` | 悬浮高亮色 |
| `--destructive` | `var(--color-error)` | 告警色 |
| `--border` | `var(--color-border)` | 全局边框 |
| `--input` | `var(--color-border-warm)` | 输入框使用更暖的边框 |
| `--ring` | `var(--color-focus)` | 统一收束至暖色焦点环 |

### 2.3 Spacing Token

所有间距必须使用以下变量，禁止硬编码 `px`：

| Token | 值 | 用途 |
|-------|-----|------|
| `--spacing-xs` | 4px | 极小间距（图标与文字、badge 内边距） |
| `--spacing-sm` | 8px | 小间距（按钮组 gap、表单 gap、嵌套卡片 padding） |
| `--spacing-md` | 16px | 中间距（面板 gap/padding、grid gap、按钮行 margin-top） |
| `--spacing-lg` | 24px | 大间距（section gap、header 垂直 padding） |
| `--spacing-xl` | 32px | 特大间距（保留） |
| `--spacing-2xl` | 40px | 页面内容区水平 padding（Header + Content 严格对齐） |

### 2.4 Height Token

| Token | 值 | 用途 |
|-------|-----|------|
| `--ui-height-base` | 34px | **全局统一交互高度**。适用于 Button/Input/Select/侧边栏项目等。 |
| `--ui-height-md` | 34px | 侧边栏等价交互高度（当前等于 base，保留语义区分） |
| `--ui-height-sm` | 34px | 紧凑场景等价交互高度（当前等于 base，保留语义区分） |
| `--header-height` | 72px | 工作区页头 |
| `--composer-height` | 260px | 底部输入框占位高度 |

> **shadcn 按钮尺寸变体**（`buttonVariants`）全部锚定 `h-[34px]`：`default`、`sm`、`icon`、`icon-sm` 均为 34px。`lg` 为 `h-11`(44px)，`icon-lg` 为 `size-11`(44px)，仅用于特殊大号场景。

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

### 2.7 Z-Index 分层碾压法则

层级变量必须严格遵守以下顺序，绝对禁止同层打架或滥用极大值。

| 角色 | z-index | 说明 |
|------|---------|------|
| Header / 侧边栏 | `z-[100]` | `sticky` 或 `fixed` 基础外框 |
| Dialog 弹窗本体 | `z-[101]` | 覆盖底层框架 |
| Select / Dropdown | `z-[105]` | 必须高于 Dialog（shadcn 默认 z-50 必须被覆盖） |
| Tooltip | `z-[110]` | 顶级气泡，绝对碾压 |

---

## 3. 字体层级

### 3.1 字体族

- **Serif (Lora)**：`var(--font-serif)`，仅用于页面标题、卡片标题、品牌主操作按钮及特定 Badge。
- **Sans (Inter)**：`var(--font-sans)`，全局基准，用于正文、表单输入、交互菜单。
- **Mono (JetBrains)**：`var(--font-mono)`，代码及接口片段。

### 3.2 字号阶梯 (Scale)

| Tailwind | 像素值 | 用途规范 |
|----------|--------|----------|
| `text-xs` | 12px | Badge、小标签、极微弱注释。**注意：使用衬线体时必须附加 `letter-spacing: 0.05em` 防粘连。** |
| `text-sm` | 14px | 按钮标准字号、辅助文字、表单 Hint。 |
| `text-base`| 16px | 基础正文、输入框内文字。 |
| `text-lg` | 18px | 卡片小标题、模块次级标题。 |
| `text-xl` | 20px | 面板标题、工作区页头标题、弹窗标题。 |
| `text-2xl` | 24px | 侧边栏品牌名、特定大数字或强调标题。 |
| `text-3xl` | 32px | 页面 Hero Title（可根据屏幕使用 `clamp`）。 |

**全局字体重置**：`index.css` 的 `body, button, input, select, textarea` 统一为 `font-family: var(--font-sans)`。品牌主操作按钮（如"开始新面试"）可使用 `!font-serif` 强制覆盖以强化品牌调性。小标签 `<Badge>` 组件统一采用品牌衬线，通过字距（tracking-wider）保持精致呼吸感。

---

## 4. 空间架构与隔离 (Spatial Anchoring)

### 4.1 Header 与 Content 的视觉隔离

控制区 (Header) 与内容区 (Content) 之间严禁背景色完全相同导致“空间塌陷”。必须通过以下三种手段之一进行强制隔离：

1. **物理底线**：`border-bottom: 1px solid var(--color-border)` 支撑结构。
2. **色彩微差**：Header 使用 `var(--color-surface)`，Content 使用 `var(--color-bg)` 形成 Z 轴错落。
3. **半透明模糊**：Sticky Header 采用 `background: color-mix(in srgb, var(--color-surface) 85%, transparent)` + `backdrop-filter: blur(12px)`。

### 4.2 双栏同构基因 (Dual-column Layout)

复杂弹窗（如全局设置 Modal）的双栏结构必须在视觉上映射全局工作区：
- **左侧导航栏**：使用 `var(--color-surface)` 作为基底，悬浮态 `var(--color-surface-hover)`，激活态 `var(--color-surface-muted)` + `var(--color-brand)` 文字。
- **右侧主内容**：使用 `var(--color-bg)` （或等价的 shadcn 映射 `var(--background)`）形成深度下沉。

### 4.3 侧边栏 (Sidebar) 物理模型

- 展开宽度 260px，折叠态 50px（内部组件留存 `34px` 的绝对正方形）。
- 高度统一使用 `var(--ui-height-base)` (34px)，内部间距完全复用 `var(--spacing-sm)`。
- 折叠态排列必须为 `justify-content: flex-start`，**严禁使用 `center` 或 `auto` 导致位移**。

### 4.4 组件 CSS 骨架重用

- 常见骨架（如 `.workspace-page`、`.workspace-header`、`.page-grid`、`.detail-grid`、`.button-row` 等）已统一定义在 `index.css`，**禁止在 Vue scoped 样式中重复定义基础布局**。

---

## 5. 组件规范

### 5.1 交互原语 (Button / Input)

- `Button` 和 `Input` 高度统一咬死 `34px`（即 `size="default"`）。
- 按钮 Hover 态：仅允许加深 `background-color` 或增加 `transform: translateY(-1px)` 微位移。**严禁改变 border 宽度或增加额外 box-shadow。**
- 按钮 Focus 态：统一使用 `focus-visible:ring-2 focus-visible:ring-focus`。严禁遗留 `ring-ring` 棕色脏边。（注：Focus 环属于元素自身的 `outline`/`box-shadow`，不参与 Z-Index 分层约束）。

### 5.2 弹窗体系 (Dialog & Toast)

- **Dialog**: 内部滚动区域必须使用 `overflow-y: auto; flex: 1; min-height: 0`。默认 `<DialogContent>` 通过 `z-[101]` 断绝下层干扰。业务可通过附加 `.dialog-no-close` 类名隐藏默认的关闭 X 按钮。
- **Toast/Notice**: 页面级通知统一收束至 `usePageNotice` 钩子，严禁散落直接调用 UI 库原生 Message。

### 5.3 浮层组件 (Select / Combobox / DropdownMenu / Tooltip)

- **共享低浮层视觉**：Select、Combobox 和 DropdownMenu 必须强制共享同一套 `dropdownContentClasses` 纸感 surface（基于 `cva` 或共享常量）。绝对禁止业务组件局部写 `border-black/5`、`shadow-lg`、`rounded-xl` 等制造独立外观。
- **极致克制边框**：下拉浮层不允许出现明显的外层卡片边框。如有必要，必须使用极弱的 token（如 `border-transparent` 或极低透明度的 `color-mix` 边框）+ 柔和阴影，实现肉眼无明显硬框。
- **动态高度锚定**：下拉选项（Item）的高度必须绝对跟随其触发器（Trigger）的高度：
  - Compact Trigger (`30px`) -> Compact Item (`30px`)
  - Default Trigger (`34px`) -> Default Item (`34px`)
- Menu Items 悬浮背景色使用 `var(--color-surface-hover)`，并且必须加上 `rounded-md`，绝对禁止贴边直角出现。
- 原生 `title` 属性全局禁用，必须使用 `z-[110]` 的 shadcn `<Tooltip>` 替代。

---

## 6. 动效物理学与交互状态机

### 6.1 GPU 渲染红线 (Golden Rule)

| 维度 | 强制标准 | 禁区 |
|------|----------|------|
| **属性** | 仅允许精确声明 `opacity`, `transform`, `color`, `background-color`, `border-color`, `box-shadow` 等 | 🔴 **禁止 `transition-all`**<br>🔴 **禁止 Layout 动画**（`width`/`height`/`padding` 等） |
| **曲线** | `ease-in-out` | 🔴 禁止 `ease-out` / `ease-linear` |
| **时长** | `duration-300` (300ms) | 🔴 禁止 `duration-150`/`200`/`500` |
| **形变** | `translateY(4px)` 微浮动 | 🔴 禁止 `zoom-in-95` / `zoom-out-95` 等缩放形变 |

*(唯一豁免 Layout 动画：侧边栏宽度切换。已通过 `will-change: width` 和 `transform: translateZ(0)` 强制开启硬件加速。)*

### 6.2 按钮防抽搐规范 (Anti-Flicker)

高频操作按钮必须遵守**绝对防抖与交叉溶解**法则：
1. **交叉溶解**：Loading 态的 Spinner 必须使用 `absolute inset-0` 居中覆盖。原生文字采用 `opacity-0` 隐身。严禁使用 `v-if` 将 Spinner 强塞入 DOM 导致按钮宽度瞬间拉伸/抽搐。
2. **黄金延迟拦截 (`withMinDelay`)**：所有异步提交必须包裹 `withMinDelay(apiCall, 300)`。确保请求哪怕 10ms 返回，动效也会从容播满 300ms，杜绝极速网络下的 UI 闪烁。
3. **流式特权豁免**：LLM 流式读取（SSE/WebSocket）**绝对豁免** `withMinDelay` 拦截，必须保证首字（TTFT）零延迟上屏。

### 6.3 统一进退场隐喻

全站元素的出现与消失（含 Dialog, Tooltip, Dropdown, 路由切换），统一服从以下物理学映射：
- **进场**：从下方沉水区浮出（`opacity: 0→1`, `translateY: 4px→0`）
- **离场**：向水面蒸发（`opacity: 1→0`, `translateY: 0→-4px`）

**唯一锚点代码参考（以 `InterviewView.vue` mode-switch 为例）**：
```css
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

> `brand-awaken`：登录页 Logo 苏醒动效，1.5s ease-out，
> 仅限 `.login-card__logo`，首次加载单次触发。

---

## 7. 可访问性

- 第三级文字 `--color-text-tertiary` 对比度必须维持 WCAG AA 4.5:1。
- 键盘操作时，所有交互元素必须清晰展示 `focus-visible` 暖色焦点环。
- 图标按钮必须配置 `aria-label`。

---

## 8. 绝对禁止项 (Red Lines)

1. **禁止纯色硬编码**：禁止在 Vue 或 CSS 中出现 `white`, `black`, `#hex`, `rgba`。
2. **禁止过度动效**：禁止使用 `transition-all`（必须精确声明如 `color`、`background-color` 等具体属性），禁止弹性/缩放弹簧动画，禁止 Layout 属性补间。
3. **禁止空间塌陷**：覆盖层必须通过 Z 轴和 `absolute`/`fixed` 定位，禁止在普通文档流中插入弹层挤压父级。
4. **禁止暗黑破坏**：禁止使用不带 `color-mix` 的半透明黑色背景。遮罩、悬浮必须使用 `color-mix` 混合以保证无损色相偏移。
5. **禁止原生覆盖**：禁止直接使用原生 `<select>`、原生 `title`、原生 `file input` 和原生 `window.alert`。
6. **禁止 CSS 脏覆盖**：禁止裸写全局的 `[data-state="open"] { outline: none !important }` 粗暴规则。
