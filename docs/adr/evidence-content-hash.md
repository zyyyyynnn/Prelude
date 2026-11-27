# Evidence 内容哈希

## Context

截图证据必须同时证明“来自哪版前端输入”和“文件字节未被替换”。仅提交 SHA 无法覆盖未提交输入，也会因 Git filter 产生平台差异；仅截图哈希无法识别生成它的前端状态。

## Decision

- `inputRevision` 对 `frontend/` 下已跟踪与未忽略文件排序后，用 Git filter 规范化计算单文件 hash，再组合路径、hash 与分隔符计算 SHA-256。
- 每个 surface 直接对 PNG 文件字节计算 SHA-256，写入 `surfaceHashes`。
- 清单不参与自身输入哈希；surface 路径必须是 `frontend` 证据根内的合法相对 `.png` 路径，且不得重复。
- `verify:evidence` 同时校验输入哈希、前端工作树干净、surface 集合与每个文件哈希。
- CI 先验证已提交证据，再捕获并执行 `git diff --exit-code -- docs/screenshots`；捕获上下文固定 `zh-CN` 与 `Asia/Shanghai`。
- 仅最大通道差不超过 2 且差异像素占比不超过 0.1% 时保留原图；manifest 的字节哈希仍严格校验。

## Consequences

证据把前端内容与图像字节绑定，截图被替换或输入被改写都会失败。前端输入未提交时本地验证会按设计失败；更新视觉必须先核对 actual/diff，再重生成证据。内容哈希不用于 Artifact provenance。

## Validation

`surface-manifest.test.cjs` 验证路径、哈希与 `capturedAt` 保留规则；`verify-evidence.test.cjs` 验证缺失、篡改、重复与越界路径；命令：`npm --prefix frontend run verify:evidence`。
