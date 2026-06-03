# Tailwind + shadcn-vue 样式修复 Prompt

## 1. 结论与修复摘要

3 处样式缺陷已定位，均为迁移残留。修复范围：1 个 CSS 变量定义 + 3 个文件的 `gap` Token 对齐 + 1 个文件的布局强制权重。

| # | 文件 | 问题 | 修复动作 |
|---|------|------|---------|
| 1 | `index.css` | `--shadow-modal` 未定义，弹窗无阴影 | 在 `:root` 中补齐变量，在 `@theme` 中补齐映射 |
| 2 | `LoginView.vue` L142、`UserProfilePanel.vue` L90、`LlmSettingsPanel.vue` L60 | `gap-6` 硬编码，未使用项目 Token | 替换为 `gap-lg` |
| 3 | `GlobalSettingsModal.vue` L24 | `flex` 覆盖 `grid` 依赖 CSS 类名顺序 | 改为 `!flex !flex-col` 强制权重 |

---

## 2. 修改与验证

### 修复 1：补齐 `--shadow-modal` 变量

**文件**：`frontend/src/styles/index.css`

在 `:root` 块中（`--shadow-inset` 定义之后，约 L92 附近）追加：

```css
--shadow-modal: 0 8px 32px rgba(0, 0, 0, 0.12);
```

在 `@theme` 块中（阴影区域，`--shadow-inset` 之后或末尾）追加：

```css
--shadow-modal: var(--shadow-modal);
```

**验证**：`grep "shadow-modal" frontend/src/styles/index.css` 预期 2 结果（`:root` 1 处 + `@theme` 1 处）。

### 修复 2：`gap-6` → `gap-lg`

**文件 1**：`frontend/src/views/LoginView.vue`

```diff
- <form class="flex flex-col gap-6 w-full" @submit.prevent="submitAuth">
+ <form class="flex flex-col gap-lg w-full" @submit.prevent="submitAuth">
```

**文件 2**：`frontend/src/components/workspace/UserProfilePanel.vue`

```diff
- <form class="flex flex-col gap-6" @submit.prevent>
+ <form class="flex flex-col gap-lg" @submit.prevent>
```

**文件 3**：`frontend/src/components/workspace/LlmSettingsPanel.vue`

```diff
- <form class="flex flex-col gap-6" @submit.prevent="onSubmit">
+ <form class="flex flex-col gap-lg" @submit.prevent="onSubmit">
```

**验证**：`grep -r "gap-6" frontend/src/ --include="*.vue"` 预期 0 结果。

### 修复 3：DialogContent 布局强制权重

**文件**：`frontend/src/components/workspace/GlobalSettingsModal.vue`

```diff
- class="max-w-[min(960px,90vw)] p-0 h-[60vh] min-h-[500px] flex flex-col overflow-hidden bg-transparent border-none"
+ class="max-w-[min(960px,90vw)] p-0 h-[60vh] min-h-[500px] !flex !flex-col overflow-hidden bg-transparent border-none"
```

**验证**：`grep "!flex" frontend/src/components/workspace/GlobalSettingsModal.vue` 预期 1 结果。

### 全局验证

```powershell
npm run build
# 预期: 0 Error, < 2s
```
