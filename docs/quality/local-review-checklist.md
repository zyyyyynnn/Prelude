# Local Quality Review Checklist

本清单用于本地预检，不替代 `.github/workflows/ci.yml`。

## 必备命令

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run build
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend audit --omit=dev
sentrux check E:\Prelude
git diff --check
```

## 红线扫描

### UI 硬编码 / 错误样式

```powershell
# 1. 禁止写法：transition-all / window.confirm / 原生 title=
rg -n "transition-all|window\.confirm|title=" frontend/src

# 2. 阴影与硬高度：shadow-md/lg、border-border 边框、h-[30/32/34px] 硬高度
rg -n "shadow-md|shadow-lg|border-border|h-\[30px\]|h-\[32px\]|h-\[34px\]" frontend/src

# 3. 颜色 token 旁路：原生 rgba、白/黑/暗色背景与硬编码十六进制色值
rg -n "rgba\(|dark:bg-|bg-white|text-white|bg-black|text-black|#[0-9a-fA-F]{3,8}" frontend/src

# 4. 裸 px 数值：z-index / height / width / font-size 直写
rg -n "z-index:\s*\d+|height:\s*\d+px|width:\s*\d+px|font-size:\s*\d+px" frontend/src

# 5. 业务组件中的 calc(var(--spacing-*)...)：本轮已收敛大部分命中，剩余仅允许几何计算
rg -n "calc\(var\(--spacing-" frontend/src
```

命中分类与处理约定：

- **扫描 1 / 2 / 3**：业务组件命中必须修复。token 定义文件 `frontend/src/styles/index.css` 中允许保留基础色值与 spacing 数值；命中仅出现在 `index.css` 时不算违规。
- **扫描 4**：业务组件中 `z-index: <num>`、裸 `height/width/font-size: Npx` 多为既有命中。**本轮不追溯修复**，仅在新增文件中不允许出现；既有命中应在下一次组件重构时引入 token 化。
- **扫描 5**：`calc(var(--spacing-*)...)` 不一定全部禁止。
  - 简单半阶 / 负向 spacing（`/ 2`、`* -1`）必须替换为 `var(--spacing-0-5)` / `var(--spacing-neg-xs)` 等已有 token。
  - 组件几何布局（toolbar 宽高、pill 宽 = `(100% - spacing) / N` 等）保留为 calc，但必须集中为组件 scoped CSS 变量（如 `--composer-toolbar-width`、`--segmented-pill-radius`）并在组件根 class 上声明，便于审查。

### 当前文档中的旧运行口径

```powershell
rg -n "start-real|start-demo|DemoModeService|/api/demo|8081|5174" README.md docs thesis-assets --glob "*.md" --glob "!thesis-assets/evidence/test-data/archive/**"
```

说明：历史归档或阶段过程报告中出现 Demo Twin、8081、5174 不必直接修；active 文档命中时必须判断是否应降权、归档或改写。

### 禁改区守卫

```powershell
git diff --name-only | rg "controller|dto|schema.sql|data.sql|data-dev.sql|DESIGN.md|frontend/src/index.css"
```

### CI YAML 语法

```powershell
npx --yes js-yaml .github/workflows/ci.yml
```

## PR 路径本地模拟

```powershell
$baseSha = "<上一轮 merge commit>"
git fetch origin $baseSha --depth=1
git diff --check "$baseSha...HEAD"
```

## CI 接入说明

- `sentrux check .` 已接入 CI，自 commit `b821bf7` 起作为架构规则门禁。
- JaCoCo 只生成 report artifact，不设置 coverage threshold。
- `npm audit --omit=dev` 已作为前端生产依赖门禁。
- `verify:byok` 与 `verify:dark` 已对 Vite cold-start 做等待与失败诊断加固。

## 使用约定

- 命中红线时先定位，再决定修复或豁免。
- 既有且本轮未触碰的历史命中项需记录原因，不得无说明跳过。
- 本轮触碰过的文档必须保证 `git diff --check` 干净。
