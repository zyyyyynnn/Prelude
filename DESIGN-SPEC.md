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

  /* === 颜色：文字 === */
  --color-text-primary:   #141413;
  --color-text-secondary: #5e5d59;
  --color-text-tertiary:  #87867f;
  --color-text-dark:      #3d3d3a;
  --color-text-on-dark:   #b0aea5;
  --color-text-button:    #4d4c48;

  /* === 颜色：品牌 / 强调 === */
  --color-brand:  #c96442;
  --color-coral:  #d97757;
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

### 卡片

```css
background: var(--color-surface);
border: 1px solid var(--color-border);
border-radius: var(--radius-xl);
box-shadow: var(--shadow-whisper);
padding: 24px;
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

使用场景：登录页背景角落、空态页面、页面顶部装饰带。
实现：内联 SVG 或独立 `.svg`，`pointer-events: none`，`aria-hidden="true"`。

### 6.2 缝线气泡（面试对话专用）

构造规则：
- 外轮廓：大圆角矩形 + 右上角小几何切角尾巴，`stroke: #c8c6be`，`stroke-width: 1`，`fill: none`
- 内层虚线：与外轮廓同形，向内 inset 6px，`stroke: #dddbd3`，`stroke-width: 0.75`，`stroke-dasharray: 4 4`
- 尾巴位置：用户消息气泡右上，AI 消息气泡左上
- 气泡背景色（填充在 SVG 外层容器）：用户消息 `var(--color-sand)`，AI 消息 `var(--color-surface)`
- 禁止 box-shadow

### 6.3 线条填充字形

实现方案：SVG `<clipPath>` + `<pattern>`

```svg
<svg viewBox="0 0 400 100" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <clipPath id="text-clip">
      <text x="0" y="80"
            font-family="'Lora', Georgia, serif"
            font-size="96" font-weight="500">AI</text>
    </clipPath>
    <pattern id="h-lines" x="0" y="0" width="400" height="4"
             patternUnits="userSpaceOnUse">
      <line x1="0" y1="2" x2="400" y2="2"
            stroke="#cac8c0" stroke-width="1"/>
    </pattern>
  </defs>
  <rect width="400" height="100"
        fill="url(#h-lines)"
        clip-path="url(#text-clip)" />
</svg>
```

硬约束：
- 线间距（pattern height）：3–5px，stroke-width：0.75–1px
- 字形本身无描边、无填充色
- 最小使用字号：80px，低于此尺寸线条细节丢失
- 使用场景：大标题装饰、空态页品牌字、报告页顶部

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
