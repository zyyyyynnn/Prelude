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
rg -n "transition-all|window\.confirm|title=" frontend/src
rg -n "shadow-md|border-border|h-\[30px\]|h-\[32px\]|h-\[34px\]|font-size:\s*15px|gap:\s*10px|padding:\s*0\s+8px" frontend/src
```

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
