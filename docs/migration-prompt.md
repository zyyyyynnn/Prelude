# Element Plus → shadcn-vue + Tailwind CSS 迁移执行 Prompt

> **【迁移状态】：已全量完成，Element Plus 已彻底卸载，功能验证通过。**

> **【执行纪律 — 最高指令】**：你必须严格按照 Phase 0 → Phase 5 的顺序执行。完成一个 Phase 并确保 `npm run build` 成功后，**必须停下来向我汇报**，等待我下达"进入下一阶段"的指令。**绝不允许一次性修改跨 Phase 的文件。** 若在当前 Phase 中发现需要后续 Phase 的改动才能解决，记录在汇报中但不要提前执行。
>
> 绞杀者模式：双轨共存 → 按域逐模块替换 → 全局桥接 → 卸载旧库。
> 硬约束：迁移全程不得破坏现有功能，每个 Phase 结束后 `npm run build` 必须 0 Error。
> 样式约束：现有 `--color-*` / `--spacing-*` / `--radius-*` CSS 变量体系保留不变，Tailwind 直接引用它们。

---

## Phase 0：基础设施搭建（绞杀者模式共存）

### 0.1 安装 Tailwind CSS 4.x + shadcn-vue

```powershell
cd frontend
npm install tailwindcss @tailwindcss/vite lucide-vue-next clsx tailwind-merge
```

**修改 `vite.config.ts`**：
- 在 `plugins` 数组中追加 `tailwindcss()` 插件（`@tailwindcss/vite` 导出）。
- 保留现有 `vue()` 插件，两者并存。

**修改 `frontend/src/styles/index.css`**：
- 在文件最顶部追加 `@import "tailwindcss";`（Tailwind 4.x 语法，替代旧版 `@tailwind base/components/utilities`）。
- 保留 `:root` 中所有自定义变量，**不删除任何 `--el-*` 变量**（双轨共存期 Element Plus 仍需要它们）。

**初始化 shadcn-vue**：
```powershell
npx shadcn-vue@latest init
# 选择: TypeScript, CSS variables, src/styles/globals.css (或 index.css)
```

> **⚠️ Tailwind v4 兼容提示**：shadcn-vue@latest init 默认会寻找 `tailwind.config.js`。由于我们使用 Tailwind v4 的 CSS-first `@theme` 语法（无 config 文件），如果 init 时提示找不到配置文件，请在生成的 `components.json` 中配置：
> ```json
> "tailwind": {
>   "css": "src/styles/index.css",
>   "config": ""
> }
> ```

此命令会生成 `components.json` 和 `src/lib/utils.ts`（包含 `cn()` 工具函数）。

### 0.2 tailwind.config.js 变量映射

**不新建 `tailwind.config.js`**。Tailwind 4.x 使用 CSS-first 配置，直接在 `index.css` 的 `@import "tailwindcss"` 之后用 `@theme` 块声明：

```css
@import "tailwindcss";

@theme {
  /* 色彩 — 直接引用现有 :root 变量 */
  --color-brand: var(--color-brand);
  --color-brand-light: var(--color-brand-light);
  --color-surface: var(--color-surface);
  --color-surface-hover: var(--color-surface-hover);
  --color-surface-muted: var(--color-surface-muted);
  --color-bg: var(--color-bg);
  --color-sand: var(--color-sand);
  --color-text-primary: var(--color-text-primary);
  --color-text-secondary: var(--color-text-secondary);
  --color-text-tertiary: var(--color-text-tertiary);
  --color-text-button: var(--color-text-button);
  --color-border: var(--color-border);
  --color-border-warm: var(--color-border-warm);
  --color-ring: var(--color-ring);
  --color-ring-deep: var(--color-ring-deep);
  --color-error: var(--color-error);
  --color-focus: var(--color-focus);
  --color-coral: var(--color-coral);
  --color-line-decor: var(--color-line-decor);
  --color-line-decor-light: var(--color-line-decor-light);

  /* 间距 */
  --spacing-xs: var(--spacing-xs);
  --spacing-sm: var(--spacing-sm);
  --spacing-md: var(--spacing-md);
  --spacing-lg: var(--spacing-lg);
  --spacing-xl: var(--spacing-xl);
  --spacing-2xl: var(--spacing-2xl);

  /* 圆角 */
  --radius-sm: var(--radius-sm);
  --radius-md: var(--radius-md);
  --radius-lg: var(--radius-lg);
  --radius-xl: var(--radius-xl);
  --radius-2xl: var(--radius-2xl);

  /* 字体 */
  --font-serif: var(--font-serif);
  --font-sans: var(--font-sans);
  --font-mono: var(--font-mono);
}
```

这样 `bg-brand`、`text-text-primary`、`p-md`、`rounded-lg`、`font-serif` 等 Tailwind 类名可直接使用项目 Token。

### 0.3 验证共存

```powershell
npm run build
# 预期: 0 Error。Tailwind 和 Element Plus 共存，旧组件不受影响。
```

**Phase 0 完成标志**：`npm run build` 0 Error，页面功能无变化，`@import "tailwindcss"` 已生效。

---

## Phase 1：低风险组件按域替换（全局设置弹窗 → 侧边栏 → 通用组件）

**执行原则**：按业务模块切分，不按组件类型切分。每个模块从里到外彻底替换完毕后再动下一个。

### 1.1 全局设置弹窗（GlobalSettingsModal + 子面板）

**目标文件**：
- `GlobalSettingsModal.vue`
- `UserProfilePanel.vue`
- `LlmSettingsPanel.vue`

**替换清单**：

| Element Plus | shadcn-vue | 安装命令 |
|-------------|-----------|---------|
| `ElDialog` | `Dialog` | `npx shadcn-vue@latest add dialog` |
| `ElButton` | `Button` | `npx shadcn-vue@latest add button` |
| `ElInput` | `Input` | `npx shadcn-vue@latest add input` |
| `ElForm` + `ElFormItem` | `Label` + 手写布局 | 见 Phase 2 范式转换 |
| `ElSelect` + `ElOption` | `Select` | `npx shadcn-vue@latest add select` |
| `ElTag` | `Badge` | `npx shadcn-vue@latest add badge` |

**GlobalSettingsModal.vue 迁移步骤**：
1. `ElDialog` → shadcn-vue `Dialog` + `DialogContent` + `DialogHeader`。
2. 删除 `:global(.el-dialog.global-settings-modal)` 穿透样式（L64-80），改为 Tailwind class 直接写在 `DialogContent` 上。
3. 侧边栏菜单按钮改为纯 Tailwind class，删除 Element Plus 依赖。

**UserProfilePanel.vue 迁移步骤**：
1. 删除 `ElForm` / `ElFormItem`，改为 `<Label>` + `<Input>` + 手写 flex 布局。
2. `ElButton` → shadcn-vue `Button`。
3. 由于此表单无复杂验证逻辑（仅检查字段非空），**不需要引入 zod**，直接在 `saveProfile()` 函数中手动校验即可。

**LlmSettingsPanel.vue 迁移步骤**：
1. `ElSelect` + `ElOption` → shadcn-vue `Select` + `SelectTrigger` + `SelectContent` + `SelectItem`。
2. 删除 `usePopperMatchTrigger` composable 的所有调用（shadcn-vue Select 自带定位）。
3. `ElInput` → `Input`（type="password" 的 show-password 需自定义 toggle 逻辑）。
4. `ElButton` → `Button`。
5. 高级设置折叠面板：`ElCollapse` + `ElCollapseItem` → 手写 `<Collapsible>` 或保留 `<details>` 原生元素 + Tailwind 样式。

### 1.2 侧边栏（AppSidebar.vue）

**替换清单**：

| Element Plus | shadcn-vue |
|-------------|-----------|
| `ElMessageBox.confirm()` | 封装 `useConfirmDialog`（见 Phase 3） |

**迁移步骤**：
1. 侧边栏主体无 Element Plus 组件（纯 HTML + CSS）。
2. 仅 `confirmDelete()` 函数使用 `ElMessageBox.confirm()`，迁移到 Phase 3 的全局 `useConfirmDialog`。

### 1.3 通用组件替换

| 组件 | 涉及文件 | shadcn-vue 平替 | 工作量 |
|------|---------|----------------|--------|
| `ElButton`（10 处） | 全局 8 个文件 | `Button` | 低：class 从 `ui-button ui-button--primary` 改为 `variant="default"` |
| `ElTag`（9 处） | AnalyticsView, MessageThread, ResumeManagementView, WorkspaceHeader | `Badge` | 低：`<ElTag effect="light">` → `<Badge variant="outline">` |
| `ElCard`（5 处） | AnalyticsView, ResumeManagementView | `Card` + `CardContent` | 低：结构类似 |
| `ElEmpty`（3 处） | AnalyticsView, ResumeManagementView | 自定义 `<EmptyState>` 组件 | 低：项目已有空态样式 |

### 1.4 Phase 1 验证

```powershell
npm run build
# 0 Error
# 手动验证: 全局设置弹窗、侧边栏、数据看板、简历管理页功能正常
```

---

## Phase 2：表单体系范式转换（先易后难）

**核心范式变化**：Element Plus 的 `ElForm` 是对象规则驱动（`rules: { username: [{ required: true }] }`），shadcn-vue 强依赖 `vee-validate` + `zod` 的 Schema 驱动。这不是组件平替，是验证范式迁移。

### 2.1 安装表单依赖

```powershell
npm install vee-validate zod @vee-validate/zod
npx shadcn-vue@latest add form
```

### 2.2 先易后难：LoginView.vue（3 字段，跑通闭环）

**目标**：用 LoginView 跑通 `zod Schema → vee-validate 绑定 → shadcn-vue Form 渲染` 的完整闭环。

**步骤**：
1. 定义 zod Schema：
   ```typescript
   // src/schemas/auth.ts
   import { z } from 'zod'
   export const loginSchema = z.object({
     username: z.string().min(1, '请输入用户名'),
     password: z.string().min(1, '请输入密码'),
   })
   export const registerSchema = loginSchema.extend({
     email: z.string().email('请输入有效邮箱'),
   })
   ```

2. 在 LoginView.vue 中使用 `useForm` + `toTypedSchema`：
   ```typescript
   import { useForm } from 'vee-validate'
   import { toTypedSchema } from '@vee-validate/zod'
   const form = useForm({ validationSchema: toTypedSchema(loginSchema) })
   ```

3. 模板替换：
   - `<ElForm>` → `<form @submit="form.handleSubmit(submitAuth)">`
   - `<ElFormItem label="用户名">` → `<FormField name="username" v-slot="{ componentField }">` + `<FormItem><FormLabel>用户名</FormLabel><FormControl><Input v-bind="componentField" /></FormControl><FormMessage /></FormItem>`
   - `<ElButton type="primary">` → `<Button type="submit">`

4. 删除 `ElForm` / `ElFormItem` / `ElInput` / `ElButton` 的 element-plus import。

### 2.3 再碰复杂表单：UserProfilePanel.vue（无 zod，手动校验）

已在 Phase 1.1 中完成（无复杂验证，手动校验即可）。

### 2.4 最后攻坚：LlmSettingsPanel.vue（动态 Select + 高级折叠）

**步骤**：
1. 定义 zod Schema（providerKey + model 必填，apiKey 可选，maxTokens 可选数字）。
2. `ElSelect` + `ElOption` → shadcn-vue `Select` 系列组件。
3. 删除 `usePopperMatchTrigger` 的全部 4 处调用。
4. 高级设置折叠：使用 shadcn-vue `Collapsible` 组件或原生 `<details>` + Tailwind。

### 2.5 Phase 2 验证

```powershell
npm run build
# 0 Error
# 手动验证: 登录/注册表单校验、LLM 配置保存/测试、用户资料修改
```

---

## Phase 3：反馈与通知（函数式桥接）

### 3.1 Toast：ElMessage → Sonner

```powershell
npx shadcn-vue@latest add sonner
```

**重写 `usePageNotice.ts`**：

```typescript
// 替换前（23 行 Element Plus 代码）
// import { ElMessage } from 'element-plus'
// ElMessage({ message, type, ... })

// 替换后
import { toast } from 'vue-sonner'
import 'vue-sonner/style.css'

export type PageNoticeType = 'success' | 'warning' | 'error' | 'info'

export function usePageNotice() {
  function showNotice(message: string, type: PageNoticeType = 'info') {
    toast[type](message, { duration: 2000 })
  }
  return { showNotice }
}
```

**在 `App.vue` 中挂载 Sonner 容器**：
```html
<template>
  <!-- 现有内容 -->
  <Toaster position="top-center" />
</template>
```

**删除 `index.css` 中的 `.el-message.page-notice` 系列样式**（L742-788，47 行）。

### 3.2 Confirm Dialog：ElMessageBox → 全局 useConfirmDialog Composable

**不要在每个页面里写 `<AlertDialog>` 标签。** 封装一个全局函数式桥接：

**新建 `src/composables/useConfirmDialog.ts`**：

```typescript
import { ref } from 'vue'

interface ConfirmOptions {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'destructive'
}

const isOpen = ref(false)
const options = ref<ConfirmOptions>({ title: '', message: '' })
let resolvePromise: ((value: boolean) => void) | null = null

export function useConfirmDialog() {
  function confirm(opts: ConfirmOptions): Promise<boolean> {
    options.value = { confirmText: '确定', cancelText: '取消', variant: 'default', ...opts }
    isOpen.value = true
    return new Promise((resolve) => { resolvePromise = resolve })
  }

  function handleConfirm() {
    isOpen.value = false
    resolvePromise?.(true)
  }

  function handleCancel() {
    isOpen.value = false
    resolvePromise?.(false)
  }

  return { isOpen, options, confirm, handleConfirm, handleCancel }
}
```

**在 `App.vue` 中挂载唯一一个 AlertDialog**：

```html
<AlertDialog :open="confirmDialog.isOpen.value">
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle>{{ confirmDialog.options.value.title }}</AlertDialogTitle>
      <AlertDialogDescription>{{ confirmDialog.options.value.message }}</AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <AlertDialogCancel @click="confirmDialog.handleCancel">{{ confirmDialog.options.value.cancelText }}</AlertDialogCancel>
      <AlertDialogAction @click="confirmDialog.handleConfirm">{{ confirmDialog.options.value.confirmText }}</AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

**替换调用点**：

```typescript
// 替换前
await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })

// 替换后
const { confirm } = useConfirmDialog()
const confirmed = await confirm({ title: '提示', message: '确定删除？', variant: 'destructive' })
if (!confirmed) return
```

涉及文件：`AppSidebar.vue` L39、`ResumeManagementView.vue` L75。

### 3.3 Phase 3 验证

```powershell
npm run build
# 0 Error
# 手动验证: 保存成功/失败 toast、删除确认弹窗、LLM 测试结果 toast
```

---

## Phase 4：导航组件替换

### 4.1 InterviewComposer.vue 的 ElDropdown 系列

**替换清单**：

| Element Plus | shadcn-vue | 安装命令 |
|-------------|-----------|---------|
| `ElDropdown` | `DropdownMenu` | `npx shadcn-vue@latest add dropdown-menu` |
| `ElDropdownMenu` | `DropdownMenuContent` | 同上 |
| `ElDropdownItem` | `DropdownMenuItem` | 同上 |

**迁移步骤**：
1. 简历选择器和岗位选择器的 `ElDropdown` → `DropdownMenu`。
2. `@command` 事件 → `@select` 事件。
3. 删除 `usePopperMatchTrigger` 的 `resumeDropdown` / `positionDropdown` 调用。

### 4.2 InterviewComposer.vue 的 ElInput（textarea）

**替换为 shadcn-vue `Textarea`**：
```powershell
npx shadcn-vue@latest add textarea
```

- `<ElInput type="textarea" :rows="3">` → `<Textarea :rows="3" />`
- `show-password` 功能需自定义（shadcn-vue Input 无内置密码切换，需加一个 toggle button）。
- 删除 `:deep(.el-textarea__inner)` 穿透样式（InterviewComposer L583-676），改为 Tailwind class 直接写在 `Textarea` 上。

### 4.3 Phase 4 验证

```powershell
npm run build
# 0 Error
# 手动验证: 简历/岗位下拉选择、文字/语音输入切换、消息发送
```

---

## Phase 5：卸载 Element Plus + 全局清理

**前提条件**：所有业务模块已替换完毕，`grep -r "element-plus" frontend/src/` 仅剩 `main.ts` 的 CSS import。

### 5.1 删除 Element Plus 引用

1. **`main.ts`**：删除 `import 'element-plus/dist/index.css'`（L2）。
2. **`vite.config.ts`**：删除 `vendor-element-plus` chunk 拆分逻辑（L23-25）。
3. **`index.css`**：删除全部 `--el-*` 变量定义（L62-120，59 行）。
4. **`index.css`**：删除 `.el-message.page-notice` 系列样式（L742-788，47 行）。
5. **`index.css`**：删除所有 `.el-select` / `.el-input` / `.el-dialog` 等全局覆盖样式（散布约 30 行）。

### 5.2 卸载

```powershell
npm uninstall element-plus
```

### 5.3 全量验证

```powershell
# 类型检查
npx vue-tsc --noEmit

# 构建检查
npm run build

# 搜索残留
grep -r "element-plus" frontend/src/
# 预期: 0 结果

grep -r "El[A-Z]" frontend/src/ --include="*.vue" --include="*.ts"
# 预期: 0 结果

grep -r "el-message\|el-dialog\|el-select\|el-input" frontend/src/styles/
# 预期: 0 结果
```

### 5.4 可删除的废弃文件

| 文件 | 原因 |
|------|------|
| `src/composables/usePopperMatchTrigger.ts` | Element Plus Select/Dropdown 专用，shadcn-vue 自带定位 |

---

## 按域执行顺序总结

```
Phase 0  基础设施（Tailwind + shadcn-vue 初始化，双轨共存）
  │
  ├── Phase 1.1  全局设置弹窗（Dialog + Form + Select + Input + Badge）
  ├── Phase 1.2  侧边栏（仅 ElMessageBox → useConfirmDialog）
  ├── Phase 1.3  通用组件（Button × 10, Tag × 9, Card × 5, Empty × 3）
  │
  ├── Phase 2.1  登录表单（跑通 zod + vee-validate 闭环）
  ├── Phase 2.2  用户资料表单（手动校验，无 zod）
  ├── Phase 2.3  LLM 配置表单（zod + Select + Collapsible）
  │
  ├── Phase 3.1  Toast（ElMessage → Sonner）
  ├── Phase 3.2  Confirm（ElMessageBox → useConfirmDialog + AlertDialog）
  │
  ├── Phase 4.1  InterviewComposer Dropdown 系列
  ├── Phase 4.2  InterviewComposer Textarea
  │
  └── Phase 5    卸载 Element Plus + 清理 136 行 --el-* 样式 + 删除 usePopperMatchTrigger
```

## 验证清单（每个 Phase 必须通过）

```powershell
# 构建
npm run build
# 预期: 0 Error, < 2s

# 类型检查（Phase 5 后）
npx vue-tsc --noEmit
# 预期: 0 Error

# 残留检查（Phase 5 后）
grep -r "element-plus" frontend/src/
grep -r "El[A-Z]" frontend/src/ --include="*.vue" --include="*.ts"
# 预期: 均为 0 结果
```

## 包体积预期变化

| 指标 | 迁移前 | 迁移后 |
|------|--------|--------|
| `vendor-element-plus` chunk | 313.87 KB (gzip 109.18 KB) | **0 KB**（已删除） |
| shadcn-vue 组件（按需引入） | 0 KB | ~30-50 KB (gzip ~10-15 KB) |
| Tailwind CSS（仅使用的类） | 0 KB | ~10-15 KB (gzip ~3-5 KB) |
| **净减少** | | **~90 KB gzip** |
