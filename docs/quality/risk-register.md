# Residual Dependency Risk Register

事实化记录 Prelude 尚未解决的传递依赖漏洞。每条记录都明确触发路径与缓解措施，避免营销话术。

> 上一轮（commit `31272bc`）已将 `axios` 与 `markdown-it` 升级至安全版本，
> 本台账只跟踪无法在当前约束下消除的传递依赖风险。

---

## 风险 R-001 — `form-data` CRLF injection (high)

| 字段 | 内容 |
|---|---|
| Date | 2026-06-19 |
| Commit HEAD | 31272bc |
| CVE / Advisory | GHSA-hmw2-7cc7-3qxx |
| Severity | high |
| Affected range | form-data `4.0.0` – `4.0.5` |
| Installed version | `form-data@4.0.5` |
| Is direct dep | no (transitive) |

### Dependency chain

```
frontend
└── axios@1.18.0            (直接依赖，本轮已升级)
    └── form-data@4.0.5     (axios 内部依赖，^4.0.5)
```

### Trigger path

- 漏洞位于 `form-data` 的 multipart 序列化逻辑（`FormData#getBoundary` / 字段名 CRLF 注入）。
- 该路径只由 axios 的 **Node.js adapter** 触发，用于把 `FormData` 序列化为 multipart 字节流。
- axios 浏览器路径默认使用 `xhr` / `fetch` adapter，**不经过 `form-data`**。

### Current usage in frontend

- `frontend/src/api/resume.ts:11` 使用 `new FormData()` —— 浏览器原生 FormData
- `frontend/src/api/user.ts:16` 使用 `new FormData()` —— 浏览器原生 FormData
- 全部 axios 调用走浏览器路径，不走 Node adapter

### Current mitigation

- 项目为浏览器 SPA，构建产物不携带 Node-only `form-data` 入口。
- 浏览器实际调用不触发该漏洞。

### Next review condition

- 若引入 Node.js 端 axios 调用（如 SSR/Node 工具脚本），需在引入前重新评估。
- 若 `axios` 后续版本升级带入 `form-data >= 4.0.6`（npm 上已发布的 patch），需评估升级成本。
- 若审计出现新的浏览器侧 `form-data` 利用路径，立即重新评估。

---

## 风险 R-002 — `dompurify` 多项配置污染 / bypass (moderate)

| 字段 | 内容 |
|---|---|
| Date | 2026-06-19 |
| Commit HEAD | 31272bc |
| Advisories | GHSA-vxr8-fq34-vvx9, GHSA-gvmj-g25r-r7wr, GHSA-cmwh-pvjr-275q, GHSA-cmwh-pvxp-8882 |
| Severity | moderate (聚合) |
| Affected range | dompurify `<= 3.4.10` |
| Installed version | `dompurify@3.4.7` |
| Is direct dep | no (transitive, optional) |

### Dependency chain

```
frontend
└── jspdf@4.2.1             (直接依赖，^4.2.1，npm 当前终版)
    └── dompurify@3.3.1..3.4.x (optional peer)
```

`npm explain dompurify` 明确标记为 optional：
```
optional dompurify@"^3.3.1" from jspdf@4.2.1
```

### Trigger path

- dompurify 仅在 jsPDF 解析 HTML 字符串（`jsPDF.html()`）时被调用，用来 sanitize 用户提供的 HTML。
- Prelude 不调用 `jsPDF.html()`，**仅调用 `jsPDF.addImage()`（canvas → image）**。

### Current usage in frontend

- `frontend/src/utils/pdf.ts:1`：`import jsPDF from 'jspdf'`
- `frontend/src/utils/pdf.ts:76`：`html2canvas(clone, ...)` —— 渲染 canvas
- `frontend/src/utils/pdf.ts:100`：`new jsPDF('p', 'pt', 'a4')`
- `frontend/src/utils/pdf.ts:133`：`pdf.addImage(imgData, 'JPEG', 0, 0, pdfWidth, printHeight)`
- **未调用 `pdf.html()`** —— dompurify sanitize 路径不触发。

### Current mitigation

- 报告 PDF 导出走 `html2canvas → jsPDF.addImage` 链路，完全不触发 `jsPDF.html()`，dompurify 不参与实际渲染。
- 即便 dompurify 被 jsPDF 加载，也不会被 Prelude 代码路径调用。

### Next review condition

- 若未来切换到 `jsPDF.html()` 实现（例如直接渲染 Markdown / 富文本），需在切换前升级 dompurify。
- 若 `jspdf` 发布新版本并通过 patch/minor 升级带入 `dompurify >= 3.4.11`，评估升级成本。
- 若 dompurify 出现与 `jsPDF.addImage` 或 `html2canvas` 相关的利用路径，立即重新评估。

---

## Reviewer Notes

- 本台账**不声称**当前浏览器主路径绝对安全，只声明 **当前代码路径不触发已知 CVE 的利用条件**。
- 任何引入 `axios` Node adapter、`jsPDF.html()`、或新的可选 sanitizer 依赖的 PR，都必须在 PR 描述中重新评估上述触发条件。
- 升级这两个依赖都可能触发 major / 大幅 lockfile 变动，按本项目硬性原则第 8 条，留待业务主动发起时再评估。