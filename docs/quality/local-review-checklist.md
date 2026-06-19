# Local Quality Review Checklist

本地复现 CI 必备命令 + 红线扫描。仅放命令，不放长篇说明。

---

## 必备命令

```powershell
# 后端
mvn -f backend/pom.xml test

# 前端
npm --prefix frontend run build
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend audit --omit=dev

# 架构
sentrux check E:\Prelude

# Whitespace
git diff --check
```

---

## 红线扫描

### UI 硬编码 / 错误样式

```powershell
rg -n "transition-all|window\.confirm|title=" frontend/src
rg -n "shadow-md|border-border|h-\[30px\]|h-\[32px\]|h-\[34px\]|font-size:\s*15px|gap:\s*10px|padding:\s*0\s+8px" frontend/src
```

### 旧 demo 口径

```powershell
rg -n "Demo Twin|DemoModeService|start-real|start-demo|8081|5174|/api/demo" -g "*.md"
```

### 禁改区守卫

```powershell
git diff --name-only | rg "controller|dto|schema.sql|data.sql|data-dev.sql|DESIGN.md|frontend/src/index.css"
```

### CI YAML 语法

```powershell
npx --yes js-yaml .github/workflows/ci.yml
```

---

## PR 路径本地模拟

```powershell
# 假设 PR HEAD 当前 commit，base 是其父
$baseSha = "<上一轮 merge commit>"
git fetch origin $baseSha --depth=1
git diff --check "$baseSha...HEAD"
```

如要反向验证会捕获 whitespace 错误，可在临时分支注入 trailing whitespace 并跑相同命令：
```powershell
git switch -c codex/ci-pr-whitespace-smoke
Add-Content -LiteralPath README.md -Value "`nprobe   `n" -Encoding UTF8
git add README.md
git commit -m "ci probe: trailing whitespace"
git diff --check <baseSha>...HEAD   # 应当 exit=2
git switch main
git branch -D codex/ci-pr-whitespace-smoke
```

---

## CI 暂缓说明（已撤销）

`sentrux` **已接入 CI**（自 commit `1cd55d2` 后续轮次）。`.github/workflows/ci.yml` 通过下载
`https://github.com/sentrux/sentrux/releases/download/v0.5.7/sentrux-windows-x86_64.exe`，
用 SHA256 校验后执行 `sentrux check .`。

`.sentrux/rules.toml` 顶部原注释 `# CI gate until sentrux behavior is proven stable in GitHub Actions`
保留作为历史判断记录；接入决策由用户在本轮显式确认（用户提供 upstream URL 后验证可固定安装渠道）。

如未来 sentrux upstream 重新组织 release / 改变 binary 命名，需要同步更新
`.github/workflows/ci.yml` 的下载 URL 与 SHA256。

---

## 使用约定

- 这份清单**不是** CI 的复刻，仅供本地预检。
- CI 仍然以 `.github/workflows/ci.yml` 为准。
- 任何命令输出命中红线，先定位再决定是否豁免；既有且本轮未触碰的命中项需记录豁免原因，不得无说明跳过。
