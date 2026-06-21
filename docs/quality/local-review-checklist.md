# Local Quality Review Checklist

本清单用于本地预检，不替代 `.github/workflows/ci.yml`。

## 必备命令

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run build
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:a11y
npm --prefix frontend run capture:visual
npm --prefix frontend audit --omit=dev
sentrux check E:\Prelude
git diff --check
```

各命令的范围与限制见 `docs/quality/ui-quality-system.md`。

## 红线扫描

### UI 硬编码 / 错误样式

```powershell
# 1. 禁止写法：transition-all / window.confirm / 原生 title=
rg -n "transition-all|window\.confirm|title=" frontend/src

# 2. 阴影与硬高度：shadow-md/lg、border-border 边框、h-[30/32/34px] 硬高度
rg -n "shadow-md|shadow-lg|border-border|h-\[30px\]|h-\[32px\]|h-\[34px\]" frontend/src

# 3. 颜色 token 旁路：原生 rgba、白/黑/暗色背景与硬编码十六进制色值
rg -n "rgba\(|dark:bg-|bg-white|text-white|bg-black|text-black|#[0-9a-fA-F]{3,8}" frontend/src

# 4. Tailwind arbitrary px 类
rg -n "\[[^\]]*\d+px[^\]]*\]" frontend/src

# 5. 属性侧 magic height ratio
rg -n "calc\(var\(--ui-height-[^)]+\)\s*\*\s*[0-9.]+" frontend/src

# 6. 业务组件中的 calc(var(--spacing-*)...)：本轮已收敛大部分命中，剩余仅允许几何计算
rg -n "calc\(var\(--spacing-" frontend/src

# 7. raw shadow / outline px / radius px / translate px / z-index utility
rg --pcre2 -n "box-shadow:\s+(?!var\()" frontend/src
rg -n "outline(-offset)?:\s*-?\d+px|border-radius:\s*\d+px|transform:\s*translate[XY]?\(-?\d+px\)" frontend/src
rg -n "\bz-\d+\b" frontend/src

# 一键运行（推荐）
npm --prefix frontend run verify:ui
```

命中分类与处理约定：

- **扫描 1 / 2 / 3 / 4 / 5 / 7**：业务组件命中必须修复。token 定义文件 `frontend/src/styles/index.css` 中允许保留基础色值、spacing 数值与全局 token 定义；组件 scoped CSS 变量必须使用约定前缀和语义命名。`npm run verify:ui` 是 Node 内置脚本，可替代本节扫描命令。
- **扫描 6**：`calc(var(--spacing-*)...)` 不一定全部禁止。简单半阶 / 负向 spacing（`/ 2`、`* -1`）必须替换为已有 token；组件几何布局保留为 calc，但必须集中为组件 scoped CSS 变量。
- **新增或修改行不允许引入新的裸 px；既有未触碰命中不追溯。**

### 文档旧运行口径

```powershell
rg -n "start-real|start-demo|DemoModeService|/api/demo|8081|5174" README.md docs thesis-assets --glob "*.md" --glob "!thesis-assets/evidence/test-data/archive/**"
```

历史归档或阶段过程报告中出现 Demo Twin、8081、5174 不必直接修；active 文档命中时必须判断是否应降权、归档或改写。

### 禁改区守卫

```powershell
git diff --name-only | rg "controller|dto|schema.sql|data.sql|data-dev.sql|DESIGN.md"
```

`frontend/src/styles/index.css` 是 token 维护入口；修改时必须在报告中说明新增/删除 token 的 DESIGN.md 对齐依据，不作为禁改区。

### CI YAML 语法

```powershell
npx --yes js-yaml .github/workflows/ci.yml
```

## PR 路径本地模拟

```powershell
$baseSha = "<上一轮 merge commit>"
$mergeBase = git merge-base "$baseSha" HEAD
git diff --check "$mergeBase" HEAD
```

`git diff --check` 在 PowerShell 中不接受三引号形式，必须用 merge-base 拿到 diff 起点。

## CI 接入说明

- `sentrux check .` 已接入 CI，自 commit `b821bf7` 起作为架构规则门禁。
- JaCoCo 只生成 report artifact，不设置 coverage threshold。
- `npm audit --omit=dev` 已作为前端生产依赖门禁。
- `verify:byok` 与 `verify:dark` 已对 Vite cold-start 做等待与失败诊断加固。
- UI 自动化门禁（`verify:ui` / `verify:tokens` / `verify:a11y` / `capture:visual`）已接入 CI；详细策略与限制见 `docs/quality/ui-quality-system.md`。
- CI 复用系统 Microsoft Edge channel：`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` + `channel: 'msedge'`；不再下载 Playwright Chromium。本地 dev 不受影响。

## 使用约定

- 命中红线时先定位，再决定修复或豁免。
- 既有且本轮未触碰的历史命中项需记录原因，不得无说明跳过。
- 本轮触碰过的文档必须保证 `git diff --check` 干净。
