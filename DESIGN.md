# Prelude UI 设计规范

> 本文件是 Prelude 前端样式、交互与数据展示的唯一规范入口。后续 UI、token、primitive、业务组件和必要数据源修改都必须以本文件为准。
>
> 技术栈：Vue 3 + shadcn-vue + Tailwind CSS v4 + reka-ui。

---

## 1. 总原则

- UI 使用暖色纸感体系：页面、卡片、浮层、遮罩、图表和报告都必须由集中 token 驱动。
- 禁止业务组件新增或保留非 token 颜色、透明度、尺寸、字体、阴影、圆角和 z-index。
- 修复 UI 异常时，如果根因来自 seed、接口字段、报告内容、评分趋势或薄弱点数据，必须修数据源，不允许只在前端隐藏、过滤或硬编码。
- 不保留本轮确认替换的旧 UI、旧样式、旧组件 fallback 或兼容分支；禁止两套样式体系并存。
- 本轮不扩展数据看板雷达图维度。雷达图固定三维：技术能力、表达清晰度、逻辑思维。

---

## 2. Token 体系

### 2.1 颜色

- 业务组件只能使用 CSS var、Tailwind token utility、项目语义 token 或 `color-mix()`。
- token 基础色值只允许集中定义在 `frontend/src/styles/index.css`；其他文件不得写散落基础色。
- 透明度必须使用 `color-mix(in srgb, var(--token) X%, transparent)`。
- 禁止业务组件出现非 token 色值、`rgba()`、`white`、`black`。

必备语义 token：

- `--color-bg`：全局纸感背景。
- `--color-surface`：卡片、输入区、Dialog、Dropdown、Tooltip、Toast 表面。
- `--color-surface-hover`：hover 背景。
- `--color-surface-muted`：弱底色、selected 背景。
- `--color-text-primary`、`--color-text-secondary`、`--color-text-tertiary`：三级文本。
- `--color-brand`、`--color-brand-light`：品牌主色及弱强调。
- `--color-border`、`--color-border-warm`：弱边界。
- `--color-ring`、`--color-focus`：焦点与选中环。
- `--color-error`：破坏性和错误状态。
- `--mask-overlay`：Dialog、Confirm 等遮罩。
- `--chart-technical`、`--chart-expression`、`--chart-logic`：数据看板三维图表色。
- `--brand-metaballs-1` 至 `--brand-metaballs-5`、`--brand-metaballs-bg`、`--brand-metaballs-shadow`：BrandMetaballs 专用 logo palette，用于保留旧版暖棕层次。
- `--rose-three-color`、`--rose-three-muted`：Rose Three 加载视觉。

### 2.2 shadcn-vue 映射

`index.css` 必须将 shadcn 语义变量映射到 Prelude token：

- `--background` -> `--color-bg`
- `--foreground` -> `--color-text-primary`
- `--card`、`--popover` -> `--color-surface`
- `--primary` -> `--color-brand`
- `--secondary`、`--muted` -> `--color-surface-muted`
- `--accent` -> `--color-surface-hover`
- `--destructive` -> `--color-error`
- `--border` -> `--color-border`
- `--input` -> `--color-border-warm`
- `--ring` -> `--color-focus`

### 2.3 间距

间距必须使用集中 token 或 Tailwind spacing token，不在业务组件散落裸尺寸。

- `--spacing-xs`：图标文字间距、badge 内边距。
- `--spacing-sm`：按钮组、表单小 gap、列表项内距。
- `--spacing-md`：面板和 section 常规 gap/padding。
- `--spacing-lg`：页面 header、区块大 gap。
- `--spacing-xl`、`--spacing-2xl`：页面级留白。

### 2.4 高度

- `--ui-height-base`：默认 Button、Input、Select、表单控件、send 框右侧主操作按钮。当前为 34px。
- `--ui-height-compact`：只用于 send 框左下角元信息控件及其 compact dropdown trigger/item。当前为 30px。
- `--header-height`：工作区 header。
- `--composer-height`：底部 composer 占位。

禁止：

- 业务组件写散落固定交互高度。
- 把 send 右侧发送、按住说话、语音/文字切换改成 compact。
- 把 send 左侧简历、岗位、模型、JD 元信息控件改成 base。

### 2.5 圆角、阴影、层级

圆角：

- `--radius-sm`：badge、内部小元素。
- `--radius-md`：Button、Input、Select、Dropdown item、Tooltip。
- `--radius-lg`：普通卡片内部区块。
- `--radius-xl`：页面卡片和 Dialog shell。
- `--radius-full`：头像、圆形状态点等明确圆形元素。

阴影：

- `--shadow-ring`、`--shadow-ring-deep`：轻轮廓。
- `--shadow-whisper`：Dropdown、Select、Combobox、Tooltip、Toast 的低浮层阴影。
- `--shadow-modal`：Dialog、Confirm。

层级：

- Header / Sidebar：`z-[100]`
- Dialog / Confirm：`z-[101]`
- Select / Dropdown / Combobox：`z-[105]`
- Tooltip：`z-[110]`

业务组件不得另写非 token z-index。

---

## 3. 字体

- UI 控件默认使用 `var(--font-serif)` 或 `font-serif`：button、input、select、dropdown、combobox、tooltip、sidebar、header、settings、label、helper text、badge、toast。
- `var(--font-sans)` 只允许用于对话气泡正文、面试报告正文和其他真正长阅读正文。
- `var(--font-mono)` 只用于 code/pre、接口片段和技术标识。
- 禁止下拉菜单、设置页按钮、send 框控件字体漂移到 sans。
- 字号使用 Tailwind 标准阶梯或集中 token；当前集中 token 包含 `--font-size-xs`、`--font-size-sm`、`--font-size-meta`、`--font-size-md`、`--font-size-lg`。
- 业务组件不得散落裸 `13px` / `14px` 等字号。

---

## 4. Motion

### 4.1 Motion Token

`index.css` 必须集中定义：

- `--motion-duration-base`
- `--motion-duration-slow`
- `--motion-duration-thinking`
- `--motion-ease-standard`
- `--motion-delay-min`
- 常用 transition preset，例如 color、surface、opacity、transform、shadow。

禁止业务组件散落固定 duration 或 easing。

### 4.2 动效红线

允许过渡属性：

- `opacity`
- `transform`
- `color`
- `background-color`
- `border-color`
- `box-shadow`

禁止：

- `transition-all`
- layout 属性动画，包括 width、height、padding、margin、max-height。
- 无依据缩放、弹簧、过度弹跳。
- 分散写死 duration、delay、easing。

唯一例外：Sidebar 展开/折叠宽度切换可以保留，但必须集中、可审查，并使用硬件加速提示。

### 4.3 按钮 Loading

- 按钮 loading 使用绝对居中的 spinner 覆盖。
- 原按钮文本通过 opacity 交叉溶解，不允许 DOM 替换导致宽度抽搐。
- 普通异步提交使用 `withMinDelay`，流式 LLM 输出不加延迟。

---

## 5. UI Primitives

### 5.1 Button

业务可用尺寸只保留：

- `default`
- `sm`
- `icon`
- `icon-sm`
- `compact`
- `icon-compact`

规则：

- `default`、`sm`、`icon`、`icon-sm` 使用 `--ui-height-base`。
- `compact`、`icon-compact` 使用 `--ui-height-compact`，只用于 send 左侧元信息控件。
- 删除 `lg`、`icon-lg` 业务可用大号按钮体系。
- 默认按钮宽度按内容自适应；登录 submit 可 `w-full`；Sidebar 主操作可 full width；Icon button 保持正方形。
- Focus 必须使用暖色 focus ring；图标按钮必须有 `aria-label`。

### 5.2 Input / Textarea / Select

- Input、Select 默认高度使用 `--ui-height-base`。
- Textarea 不得使用硬编码暗色背景；背景、边框、focus 全部走 token。
- Composer textarea 视觉可保留，但高度、字号、滚动与 focus 必须 token 化。
- 固定选项优先 Select；可输入/可搜索模型使用 Combobox。

### 5.3 Dropdown / Select / Combobox / Tooltip

统一低浮层视觉：

- `bg-surface`
- `border-transparent` 或极弱 token 边界
- `shadow-whisper`
- `rounded-md`
- token padding
- `font-serif`

规则：

- Dropdown、Select、Combobox content 使用共享类，不在业务组件单独写一套浮层。
- content 层级使用 `z-[105]`，Tooltip 使用 `z-[110]`。
- item 高度跟 trigger：default 34px，compact 30px。
- item 使用 nowrap、truncate、token hover、focus-visible。
- 原生 `title` 全局禁用。所有 truncate / ellipsis 动态文本必须使用 Tooltip 展示完整值。

### 5.4 Dialog / Confirm / Toast

- Dialog、Confirm 使用统一暖色半透明遮罩 `--mask-overlay`。
- shell 使用 `bg-surface`、token radius、token padding、`shadow-modal`，不得使用强硬边框。
- Confirm 必须提供 Promise 异步 API；替换 `window.confirm` 时必须改造控制流为 `const confirmed = await confirm(...)`。
- Toast 使用低浮层纸感，状态差异通过 token 色彩和图标表达，不使用独立硬阴影。

### 5.5 Badge / Card / Separator / EmptyState

- Badge 保持胶囊形，使用 token 背景、边框和字体。
- Card 默认不使用强阴影。
- Separator 只使用弱边界色。
- EmptyState 保持安静，不默认增加操作按钮。

---

## 6. 业务组件规范

### 6.1 Login

- 保持纸感背景和登录卡片。
- BrandMetaballs 必须使用专用 logo token palette，不得写散落品牌色；视觉目标是延续旧版暖棕层次，不因 token 化变成新视觉。背景圆盘必须柔和，贴近所在容器纸感，避免成为沉重的实心圆盘（logo background token should stay close to the surrounding surface and must not read as a separate heavy disk）。
- 登录/注册切换继续使用 SegmentedControl。
- 密码可见按钮为透明图标按钮，必须有 `aria-label` 和 focus 样式。
- submit 使用 base 高度，登录页允许 full width。

### 6.2 App Shell / Sidebar / Header

- 主应用保持 100vh flex shell。
- Sidebar 展开 260px，折叠约 51px；折叠态隐藏品牌文字。
- Sidebar item 只显示岗位名，长文本 Tooltip；active/hover 只用背景和文字色，不加左侧条。
- Header 标题长文本 Tooltip；状态 Badge 只展示业务状态/阶段，不展示数据库 id。
- 面试/报告切换继续放 header 右侧。

### 6.3 Message Thread

- 消息区上方滚动，composer 固定底部。
- 消息正文纯文本，不渲染 Markdown。
- 对话气泡正文使用 sans；角色、时间、评分、标签使用 serif。
- 即时评分拆成 score pill 和 hint preview；长 hint 用 Tooltip。
- 思考中和重连状态保留，但动效必须符合 motion 规则。

### 6.4 Composer

- 空状态 composer 居中；欢迎语从多个文案中随机展示。
- 文本/JD textarea 去掉裸高度和裸字号，使用 token。
- send 左侧元信息控件：简历、岗位、模型、JD 使用 compact 30px。
- send 右侧主操作：开始面试、发送、按住说话、语音/文字切换使用 base 34px。
- 简历 dropdown 保留上传 PDF；岗位 dropdown 只显示岗位名。
- 模型入口改为 dropdown：只切当前 provider 的模型，不切 provider；底部提供进入 LLM 配置页入口。
- 语音/文字切换只显示图标，必须有 `aria-label`。
- 面试结束后输入区整体置灰且不可操作。

### 6.5 Report

- 报告生成中保留居中状态卡片，但不保留沙漏字符；使用 token 化 Rose Three 或统一加载视觉。
- 报告正文保留约 800px 纸面容器。
- Markdown 标题用 serif；正文、列表、表格内容用 sans；code/pre 用 mono。
- Markdown 必须覆盖 h1-h4、p、ul/ol/li、blockquote、table、code/pre、长报告滚动。

### 6.6 Settings

- 设置弹窗保持左侧导航、右侧内容双栏。
- 设置页包含账号资料、主题、LLM 配置三个 tab。
- 退出登录放左侧底部；保存、测试按钮放右上角；内容较长时只滚动右侧内容。
- 用户名可编辑；邮箱可编辑；密码区只保留旧密码和新密码。
- 新增真实头像上传：后端保存头像 URL/路径，未设置时展示用户名首字母。
- 新增主题切换：浅色、暗色、跟随系统；使用小卡片式 SVG 选项，不做普通按钮。
- LLM provider 文案统一“OpenAI 兼容协议”；Thinking Depth 增加“默认（Default）”。

### 6.7 Resume Management

- 页面标题不加说明文案。
- 上传按钮保留在页面右上角。
- 统计卡片保留。
- 文件名长文本 truncate + Tooltip。
- 删除已占用简历时按钮禁用；删除确认使用统一 Confirm。

### 6.8 Analytics

- Score cards 固定技术、表达、逻辑三项。
- 雷达图固定三维：技术能力、表达清晰度、逻辑思维；不增加维度。
- 趋势图显示最近五次真实评分数据，日期来自真实 `score_history.created_at` 或关联 session 时间。
- ECharts 颜色、tooltip、axis、grid 全部 token 化。
- 薄弱点按类别聚合；同一弱点下描述同层级展示，不出现 summary/detail 伪层级。

---

## 7. 主题、资料与数据边界

### 7.1 主题

- 主题取值固定为 `light | dark | system`。
- 登录用户主题偏好保存到后端；未登录用户保存到 localStorage。
- 应用启动时根据偏好设置根节点主题；system 跟随系统变化。
- 暗色主题切换必须通知依赖 canvas、chart、shader 的组件重新解析 token 并重绘。
- 暗色主题必须通过 token 映射完成，业务组件不得写 `dark:bg-*` 作为独立视觉。

### 7.2 用户资料与头像

后端 contract：

- `username`
- `email`
- `avatarUrl`
- `themePreference`

数据库字段：

- `avatar_url`
- `theme_preference`

头像存储：

- 只允许本地文件系统。
- 上传目录为项目配置的本地 uploads 路径。
- 通过 Spring WebMvc 静态资源映射暴露。
- 禁止引入云存储依赖。
- 禁止把图片 Base64 存入数据库。

### 7.3 数据源

- demo seed 必须幂等，不删除重建真实用户、自定义岗位、自建会话或真实简历。
- demo session 不依赖自增 id，不重置 AUTO_INCREMENT。
- UI 不展示数据库内部 id。
- 趋势日期、报告、评分、薄弱点必须来自真实后端数据。
- 如果修改 `schema.sql` 或 `data.sql`，必须执行后端验证。

---

## 8. Rose Three 加载动画

- 使用原生 SVG + `requestAnimationFrame`，不引入 Three.js、Lottie 等重型库。
- 使用 Math Curve Rose Three 方向，粒子渲染绕过 Vue 响应式热路径。
- 颜色使用 `currentColor` 或 Rose Three token。
- 宽高使用 `1em`，由外层 Tailwind/token 控制。
- 支持 `speedMultiplier`，速度与全局 motion token 结合。
- 用于报告生成、AI 思考、长等待状态；普通按钮 loading 仍使用 Button loading。

---

## 9. 静态扫描红线

本轮结束时业务代码不得命中：

- `transition-all`
- 原生 `title=`
- `window.confirm`
- `shadow-md` / `shadow-lg`
- 无依据 `border-border`
- `rgba(`
- 非 token 色值
- `white` / `black`
- 散落固定交互高度
- 裸字号
- 分散 fixed duration / easing
- `dark:bg-*`
- 内联硬编码颜色、背景、边框、阴影

token 定义文件中的基础色值允许集中存在，但必须人工确认不泄漏到业务组件。
