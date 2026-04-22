# UI 设计规范

> 只写硬数值与约束，无氛围描述。可直接作为 Agent 提示词附件使用。

---

## 1. CSS 变量（全局注入 `:root`）

```css
:root {
  /* === 字体 === */
  --font-serif: 'Lora', Georgia, serif;
  --font-sans:  'Inter', system-ui, sans-serif;
  --font-mono:  'JetBrains Mono', monospace;

  /* === 颜色：背景 / 表面 === */
  --color-bg:           #f5f4ed;
  --color-surface:      #faf9f5;
  --color-surface-dark: #30302e;
  --color-bg-dark:      #141413;
  --color-sand:         #e8e6dc;
  --bg-paper-filter:
    radial-gradient(circle at 16% 14%, rgba(255,255,255,0.32) 0, rgba(255,255,255,0) 26%),
    radial-gradient(circle at 80% 72%, rgba(0,0,0,0.04) 0, rgba(0,0,0,0) 24%),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 320 320'%3E%3Cfilter id='paper'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.82' numOctaves='4' stitchTiles='stitch'/%3E%3CfeColorMatrix type='saturate' values='0'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23paper)' opacity='0.12'/%3E%3Cg fill='none' stroke='%23beb7a8' stroke-opacity='0.2' stroke-width='1'%3E%3Cpath d='M18 42c24 11 51 -5 79 7 20 9 42 12 67 -1'/%3E%3Cpath d='M214 34c17 8 31 3 45 9 15 6 27 5 43 -3'/%3E%3Cpath d='M42 120c17 -7 35 6 57 0 18 -5 36 -3 51 5'/%3E%3Cpath d='M176 138c19 5 35 -7 60 -2 15 3 29 4 46 -5'/%3E%3Cpath d='M26 210c22 7 46 -8 72 4 18 8 33 11 56 1'/%3E%3Cpath d='M182 228c17 -6 36 6 60 1 16 -3 32 -1 51 7'/%3E%3Cpath d='M74 286c18 -8 38 4 57 -1 17 -5 31 -3 48 6'/%3E%3Cpath d='M208 284c13 4 29 -6 47 -2 14 3 28 2 40 -5'/%3E%3C/g%3E%3C/svg%3E");

  /* === 颜色：文字 === */
  --color-text-primary:   #141413;
  --color-text-secondary: #5e5d59;
  --color-text-tertiary:  #87867f;
  --color-text-dark:      #3d3d3a;
  --color-text-on-dark:   #b0aea5;
  --color-text-button:    #4d4c48;

  /* === 颜色：品牌 / 强调 === */
  --color-brand:  #9e7b6a;
  --color-coral:  #b08878;
  --color-error:  #b53333;
  --color-focus:  #3898ec;

  /* === 颜色：边框 === */
  --color-border:       #f0eee6;
  --color-border-warm:  #e8e6dc;
  --color-border-dark:  #30302e;

  /* === 颜色：阴影环 === */
  --color-ring:      #d1cfc5;
  --color-ring-deep: #c2c0b6;

  /* === 装饰线条（SVG 专用） === */
  --color-line-decor:       #c8c6be;
  --color-line-decor-light: #dddbd3;
  --color-line-fill:        #cac8c0;

  /* === 圆角 === */
  --radius-sm:  6px;
  --radius-md:  8px;
  --radius-lg:  12px;
  --radius-xl:  16px;
  --radius-2xl: 24px;
  --radius-3xl: 32px;

  /* === 阴影 === */
  --shadow-ring:     0px 0px 0px 1px var(--color-ring);
  --shadow-ring-deep: 0px 0px 0px 1px var(--color-ring-deep);
  --shadow-whisper:  rgba(0,0,0,0.05) 0px 4px 24px;
  --shadow-inset:    inset 0px 0px 0px 1px rgba(0,0,0,0.15);

  /* === Element Plus 覆盖 === */
  --el-color-primary: #9e7b6a;
}
```

### 页面背景实现

```css
body {
  background-color: var(--color-bg);
  background-image: var(--bg-paper-filter);
  background-attachment: fixed;
  background-repeat: no-repeat, no-repeat, repeat;
  background-size: auto, auto, 320px 320px;
  background-blend-mode: normal, normal, multiply;
}
```

---

## 2. 字体引入（index.html `<head>`）

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Lora:wght@400;500&family=Inter:wght@400;500&family=JetBrains+Mono:wght@400&display=swap" rel="stylesheet">
```

---

## 3. 字型系统

| 角色 | 字体变量 | 字号 | 字重 | 行高 | 字距 |
|---|---|---|---|---|---|
| Hero 标题 | `--font-serif` | 64px | 500 | 1.10 | normal |
| 页面标题 | `--font-serif` | 52px | 500 | 1.20 | normal |
| 区块标题 | `--font-serif` | 36px | 500 | 1.30 | normal |
| 卡片标题 | `--font-serif` | 25px | 500 | 1.20 | normal |
| 小标题 | `--font-serif` | 20px | 500 | 1.20 | normal |
| 正文大 | `--font-sans` | 20px | 400 | 1.60 | normal |
| 正文标准 | `--font-sans` | 16px | 400 | 1.60 | normal |
| 正文小 | `--font-sans` | 15px | 400 | 1.60 | normal |
| UI / 导航 | `--font-sans` | 17px | 400–500 | 1.25 | normal |
| 按钮文字 | `--font-sans` | 16px | 500 | 1.25 | normal |
| 标签 / Badge | `--font-sans` | 12px | 500 | 1.25 | 0.12px |
| 辅助说明 | `--font-sans` | 14px | 400 | 1.43 | normal |
| 代码 | `--font-mono` | 15px | 400 | 1.60 | -0.32px |

**硬约束**：
- `--font-serif` 只用于标题层级，禁止用于按钮、标签、输入框
- serif 字重上限 500，禁止 600 / 700 / bold
- body 行高不低于 1.40

---

## 4. 组件规范

### 按钮

**主 CTA（品牌色）**
```css
background: var(--color-brand);
color: var(--color-surface);
border-radius: var(--radius-lg);
padding: 10px 20px;
font: 500 16px var(--font-sans);
box-shadow: var(--shadow-ring);
```

**次要按钮（沙色）**
```css
background: var(--color-sand);
color: var(--color-text-button);
border-radius: var(--radius-md);
padding: 8px 12px;
font: 500 16px var(--font-sans);
box-shadow: var(--shadow-ring);
```

悬停态：`box-shadow` 升级为 `var(--shadow-ring-deep)`，背景亮度 -3%
禁用态：`opacity: 0.45; pointer-events: none`

### 头部菜单（AppHeaderMenu）

- 触发器位置：页面右上角，单按钮呈现
- 触发器尺寸：42×42px
- 触发器外观：`background: var(--color-surface)`，`border: 1px solid var(--color-border-warm)`，`border-radius: var(--radius-md)`，`box-shadow: var(--shadow-ring)`
- 图标：内嵌 SVG 三横线，禁止额外文字标签
- 展开方式：从触发器左侧弹出，不得下拉到触发器下方
- 覆盖关系：动作条不得覆盖原有菜单按钮，必须与触发器保留独立间距并完整露出按钮本体
- 动作条：`主菜单栏`、`用户设置`、`LLM配置`、`退出` 四个按钮横向单行排列，与触发器垂直居中并保持同一水平线
- 触发器与动作条间距：8–10px
- 动作条容器：仅作为无框布局层存在，不允许额外外边框、底板色块或整体阴影
- 菜单项：非胶囊，最小宽度 104px，圆角 `var(--radius-md)`，`border: 1px solid var(--color-border)`，使用轻量 hover / active 背景变化
- 当前页高亮：只允许轻量底色与边框变化，禁止“当前”文字标记，禁止整块高饱和填充
- 动画：仅允许 `opacity + translateX`，120–160ms；禁止弹簧、缩放、胶囊滑块
- 响应式：窄屏宽度不足时允许回退为纵向紧凑菜单，但不得遮挡品牌区或超出视口
- 桌面端展开：动作条作为触发器左侧独立元素与按钮同线排布，不允许覆盖或压住触发器本体

### Hero 栏（AppHeader）

- 适用页：当前所有登录后业务页统一使用，包括 `主工作台`、`LLM配置`、`用户设置`、`数据看板`
- 结构：品牌区在左，菜单触发器在右；整条 header 作为一个独立容器呈现
- 布局：左右拓宽铺满页面，桌面端宽度按 `calc(100% - 24px)` 处理，不再使用居中窄卡片
- 定位：顶部常驻，使用 `position: sticky`；桌面端 `top` 建议 10px，移动端可收至 6–8px
- 质感：允许使用毛玻璃效果，`background` 需为半透明浅色表面，配合 `backdrop-filter: blur(...)`
- 外观：必须保留轻边框、轻阴影和暖灰底色，不得做冷色蓝调玻璃
- 圆角：非胶囊，建议使用 `6–8px` 的小圆角
- 禁止：下侧额外加一条独立竖线装饰带；当前两页 hero 栏必须完全统一

### 输入框

```css
background: var(--color-surface);
border: 1px solid var(--color-border-warm);
border-radius: var(--radius-lg);
padding: 10px 14px;
font: 400 16px var(--font-sans);
color: var(--color-text-primary);
```
焦点态：`outline: 2px solid var(--color-focus); outline-offset: 1px`

### 上传行（UploadField）

- 结构：左侧 `选择文件` 按钮，右侧文件名展示；单行排列
- 最小高度：62px
- 外框：`1px solid var(--color-border-warm)`，圆角 `var(--radius-md)`
- 内边距：12px 14px
- 文件名：单行省略，`overflow: hidden; text-overflow: ellipsis`
- 禁止直接暴露未收口的原生 file input 大块样式

### 设置入口（SettingsEntry）

- `用户设置` 与 `LLM配置` 不再出现在页面正文区域作为次级切换按钮
- 两个入口统一收纳到头部菜单动作条中，与 `主菜单栏`、`退出` 并列
- 设置页正文只保留页面标题、说明文案与业务卡片，不再重复显示顶层导航

### 卡片

```css
background: var(--color-surface);
border: 1px solid var(--color-border);
border-radius: var(--radius-xl);
box-shadow: var(--shadow-whisper);
padding: 24px;
```

### 应用主面板（PanelCard）

- 用途：主工作台、LLM 配置页等主要业务卡片
- 外观：沿用标准卡片边框、圆角、阴影
- 内边距：28px
- 说明标签：允许在面板标题右侧放置 1 个轻量辅助标签，用于表达模块角色或内容范围，例如 `Provider 与模型`、`邮箱与密码维护`
- 禁止：直接暴露接口路径、开发内部命名或 `一期/二期/三期` 之类阶段提示
- 内部节奏：区块间距 22px；表单组 / 字段组间距 16px；按钮组间距 12px
- 分栏面板：允许在卡片内部使用左右双栏，但列间距不得小于 22px

### 登录卡片（LoginCard）

- 布局：页面垂直水平居中，卡片固定宽度 980px，左右双栏；左栏放品牌信息与 SVG logo 预留位，右栏放登录 / 注册内容
- 品牌整合：登录页不再显示页面级品牌头，`模拟面试系统` 与 `INTERVIEW PLATFORM` 必须整合进卡片左栏
- 品牌排布：左栏品牌区使用两列布局，左侧品牌块固定 54px，右侧 eyebrow 与标题垂直排布并居中对齐
- 标题：`模拟面试系统` 在桌面端禁止换行，宽度不足时优先缩小字号而不是折行
- 外框：容器原生 1px solid var(--color-line-decor)，#c8c6be；必须完整闭合，底边不得裁切或发虚
- 内侧虚线框：距外框 inset 8px，stroke-dasharray: 4 4，颜色 var(--color-line-decor-light)，#dddbd3
- 圆角：var(--radius-lg)，12px
- 内边距：38px 42px 40px
- 左栏：品牌块置顶，中部预留独立 SVG logo 区域；删除底部说明提示文案，不放输入框、不放角落装饰 SVG
- 左栏预留位：logo 容器最小高度 300px；内部放与主题一致的“文档 / 简历”线稿 SVG，只保留文档轮廓、折角和 3 条笔记线，不放默认文案，不再保留额外占位外框
- 右栏：登录 / 注册在同一卡片内切换，表单区最大宽度 520px
- 右栏文案：删除标题下方辅助提示词，只保留 eyebrow 与主标题
- 按钮：登录 / 注册切换按钮移动到表单底部，与主提交按钮同一行；按钮组宽度与右侧标题 / 表单列保持一致，使用双列等宽排布；按钮间距 18px；禁止顶部再出现一组切换按钮
- 高度稳定：登录态必须预留与注册邮箱输入同高的空位，切换登录 / 注册时卡片整体尺寸不得变化
- 禁止：box-shadow、background-color、backdrop-blur
- 卡片内表单元素背景色使用 var(--color-surface) #faf9f5，与页面纸感底色形成轻微分层

```html
<div class="login-card">
  <svg class="login-card-border" aria-hidden="true">
    <rect width="100%" height="100%"
        rx="8" ry="8"
        stroke="#dddbd3" stroke-width="0.75"
        stroke-dasharray="4 4" fill="none"/>
  </svg>
</div>
```

### 标签 / Badge

```css
background: var(--color-sand);
color: var(--color-text-secondary);
border-radius: var(--radius-2xl);
padding: 4px 10px;
font: 500 12px var(--font-sans);
letter-spacing: 0.12px;
```

### 状态提示（StatusBanner）

- 用途：默认用于用户操作结果反馈，例如保存成功、上传失败、创建面试成功、请求异常；设置页允许保留一条初始化成功提示，例如 `配置已加载`
- 位置：页面顶部居中显示，不能插入表单或卡片内部
- 宽度：建议 `280–304px`，不要占满内容列
- 时长：统一自动消失，建议 `2000ms`
- 外观：`background: var(--color-surface)`，`border: 1px solid var(--color-border-warm)`，圆角 `var(--radius-md)`
- 内边距：12px 14px

### 回放消息层级（ReplayMessage）

- system：浅底说明块，允许使用虚线边框；不得渲染成聊天气泡
- user：靠右显示，背景使用 `var(--color-sand)`
- assistant：靠左显示，背景使用 `var(--color-surface)`
- 元信息：时间与角色标签放在消息头部，字号 14px

### Markdown 报告面板（MarkdownReportPanel）

- 容器：`1px solid var(--color-border)`，圆角 `var(--radius-md)`，背景 `var(--color-surface)`
- 内边距：22px 24px
- 最小高度：300px
- 正文：16px 以下只能用于代码与辅助说明；正文行高不低于 1.65
- 标题：复用 serif 标题体系；禁止回退到 `<pre>` 纯文本面板
- 代码块：允许使用 `var(--font-mono)` 与浅沙色底，但不能使用深色终端风格

### 数据看板图表容器（AnalyticsPanel）

- 图表只能放在标准卡片内，不允许独立悬浮
- 图表容器最小高度：300px
- 图表主色：沿用 `--color-brand` / `--color-coral` / 中性暖灰，不新增高饱和彩色主题
- 图表颜色来源：必须读取现有 CSS token；禁止在脚本里硬编码十六进制颜色
- 无数据：显示空态，不渲染空图

---

## 5. 深度 / 阴影系统

| 层级 | CSS | 使用场景 |
|---|---|---|
| Level 0 | 无 | 页面背景、内联文字 |
| Level 1 | `1px solid var(--color-border)` | 标准卡片、区块 |
| Level 2 | `var(--shadow-ring)` | 交互卡片、按钮、hover 态 |
| Level 3 | `var(--shadow-whisper)` | 浮层卡片、截图容器 |
| Level 4 | `var(--shadow-inset)` | 按钮按下态 |

禁止：`rgba(0,0,0,>0.15)` 的重阴影。

---

## 6. 装饰系统（SVG 线稿）

### 6.1 线稿规范

| 属性 | 值 |
|---|---|
| 实线 stroke | `#c8c6be` |
| 虚线 stroke | `#dddbd3` |
| stroke-width | 0.5–1px |
| fill | none（全部无填充） |
| 虚线节奏（标准） | `stroke-dasharray: 4 4` |
| 虚线节奏（细密） | `stroke-dasharray: 2 6` |
| 风格 | 几何线框、直线与圆弧组合；禁止有机曲线、禁止渐变 |

使用场景：登录卡片边框、空态页面、页面顶部装饰带。
实现：内联 SVG 或独立 `.svg`，`pointer-events: none`，`aria-hidden="true"`。

落地位置（强制，不得省略）：
- 登录注册页：品牌信息整合到卡片左栏；仅保留覆盖整张卡片的双层边框 SVG，禁止左上角 / 右下角额外装饰 SVG
- 空态页面（无简历、无历史记录）：居中插图区域用线框几何 SVG 替代图片占位，尺寸不超过 200×200px
- 顶部装饰带：本轮暂不使用；登录后页面统一采用毛玻璃 Hero 栏，不额外叠加下侧竖线装饰
- 报告页标题区：仅在后续独立报告页使用线条填充字形；本期主工作台内联报告区不放 `REPORT` Hero 装饰

禁止：装饰 SVG 出现在输入框、按钮、数据表格、消息气泡内容区附近 20px 范围内

### 6.2 缝线气泡（面试对话专用）

构造规则：
- 外轮廓：大圆角矩形 + 右上角小几何切角尾巴，`stroke: #c8c6be`，`stroke-width: 1`，`fill: none`
- 内层虚线：与外轮廓同形，向内 inset 6px，`stroke: #dddbd3`，`stroke-width: 0.75`，`stroke-dasharray: 4 4`
- 尾巴位置：用户消息气泡右上，AI 消息气泡左上
- 气泡背景色（填充在 SVG 外层容器）：用户消息 `var(--color-sand)`，AI 消息 `var(--color-surface)`
- 禁止 box-shadow

---

## 7. 禁止项

- 禁止冷色系灰（带蓝调灰）
- 禁止页面背景使用 `#ffffff`，必须用 `var(--color-bg)` 或 `var(--color-surface)`
- 禁止 serif 字重超过 500
- 禁止按钮、输入框圆角低于 8px
- 禁止重阴影（`rgba(0,0,0,>0.15)` drop shadow）
- 禁止装饰 SVG 有任何 fill 颜色
- 禁止线条填充字形低于 80px 使用
- 禁止硬编码颜色值，必须使用 CSS 变量
