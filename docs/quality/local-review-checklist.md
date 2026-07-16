# Local Quality Review Checklist

本清单用于本地交付预检，命令与 `.github/workflows/ci.yml` 保持一致。

## 前端门禁

```powershell
npm --prefix frontend run check
npm --prefix frontend run verify:architecture
npm --prefix frontend run test:contracts
npm --prefix frontend run build
npm --prefix frontend run verify:production
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:flows
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:a11y
npm --prefix frontend run verify:visual
npm --prefix frontend audit --omit=dev
```

`build` 必须保留 `vue-tsc --noEmit && vp build` 语义。`verify:architecture` 必须同时执行规则单测与真实源码扫描。

## 仓库门禁

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml "-Dtest=HybridRetrievalCapacityTest" "-Dprelude.benchmark=true" "-Djacoco.skip=true" test
sentrux check .
git diff --check
```

涉及后端协议或持久化语义时，`mvn test` 是必跑门禁；纯前端改动仍用它确认整仓兼容性。容量实验输出 P50/P95 与 Recall@5，但不设置机器相关时延阈值。

CI 的 `schema` job 必须在 MySQL 8.4 上通过全新建库、旧结构/Provider 值升级和重复执行。`schema.sql`、`data.sql`、`data-dev.sql` 之外不得新增日期命名迁移脚本。

## 边界确认

```powershell
# 仅允许 app / features / shared / devtools 四个源码目录
Get-ChildItem frontend/src -Directory | Select-Object -ExpandProperty Name

# 旧顶层路径、反向依赖、跨 feature 深导入由脚本统一检查
npm --prefix frontend run verify:architecture

# 论文资产有改动时必须能回溯到治理阶段和证据来源
$mergeBase = git merge-base main HEAD
git diff --name-only $mergeBase HEAD | rg "^thesis-assets/"

# 不得残留兼容转发引用和已移除依赖
rg -n "@/(api|components|composables|lib|router|schemas|stores|styles|utils|views)/|radix-vue" frontend --glob "!package-lock.json"
```

第三条仅在论文治理任务中允许有输出；第四条应无输出。源码目录除四层目录外只允许根级类型声明文件。

## UI 不变量

- `DESIGN.md` 仍是 UI 唯一最高规范。
- token 基础值只在 `frontend/src/shared/ui/styles/index.css` 维护。
- 除明确批准的组件合同调整外，架构重构不得调整品牌色、间距、圆角、阴影、动效或页面布局。
- `verify:ui` 与 `verify:tokens` 必须同时通过；浏览器可用时还需通过 a11y 与 visual。

## 差异检查

```powershell
$mergeBase = git merge-base main HEAD
git diff --stat $mergeBase HEAD
git diff --check $mergeBase HEAD
git diff --name-status $mergeBase HEAD
```

最终审查以当前终态代码、测试和文档为准，不保留迁移日志或阶段性说明。
