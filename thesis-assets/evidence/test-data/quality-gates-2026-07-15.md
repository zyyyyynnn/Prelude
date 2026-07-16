# 质量门禁证据快照（2026-07-15）

## 基线

- 代码基线：`391171607d553a059c5aedb6baa69cd6d9148ac8`（PR #27 合并提交）
- 审查入口：PR #27 `<https://github.com/zyyyyynnn/Prelude/pull/27>`、主线 CI run `29471168463`
- 证据状态：候选待用户与审查官复核，不构成正式冻结

## 本地验证

| 范围 | 验证结果 |
| --- | --- |
| 后端全量 | `mvn -q -f backend/pom.xml clean test`：255 个测试，0 failure，0 error，1 个容量基准按设计跳过；JaCoCo application 包门禁通过 |
| BYOK 安全与契约 | 出站策略、DNS 重绑定、重定向、响应上限、三协议请求与流解析的聚焦测试通过 |
| MySQL 8.4 | 全新建库、模拟旧结构/旧 Provider 数据、重复执行均通过；结果为 4 个 Provider，旧值映射正确 |
| 前端静态与构建 | `npm run check`、`npm run build` 通过；build 保留 `vue-tsc --noEmit` 与 Vite+ 构建语义 |
| 前端架构与契约 | architecture 9/9、production 4/4、contract 8/8、flows 3/3 全部通过 |
| UI 门禁 | UI、token、BYOK、dark 通过；dark 竞态修复后本地连续 5 次通过；a11y 9/9 且 critical 为 0；visual 24/24 |
| 依赖审计 | `npm audit --omit=dev`：0 vulnerability |
| 仓库质量 | CI 固定的 Sentrux `0.5.7`：10 条规则通过，Quality 6658；基线提交 `7a3535c` 同版本复测亦为 6658，未下降 |
| 仓库卫生 | `docker compose config --quiet`、`git diff --check` 通过；无 `backend/**` 或 `thesis-assets/chapters/**` 非预期修改 |

## 远端 CI

| 触发 | Run | 提交 | 结果 |
| --- | --- | --- | --- |
| 最终证据 push | [29470508756](https://github.com/zyyyyynnn/Prelude/actions/runs/29470508756) | `54f531d` | `schema`、`build` 全部成功 |
| pull request | [29470510655](https://github.com/zyyyyynnn/Prelude/actions/runs/29470510655) | `54f531d` | 第一次 build 因 Maven Central 临时返回 403 失败；同提交重跑后 `schema`、`build` 全部成功 |
| `main` push | [29471168463](https://github.com/zyyyyynnn/Prelude/actions/runs/29471168463) | `3911716` | 合并态 `schema`、`build` 全部成功 |

三条最终成功的 build 均执行 Sentrux、后端测试与 JaCoCo、前端依赖审计、check/build、架构/契约、UI/token、BYOK、dark、关键 flows、a11y 和 visual 采集；未通过降低门禁获得绿色结果。

## 容量证据

`retrieval-capacity-2026-07-15.md` 记录 5000 文档、300 查询、64 维合成数据的本地结果：索引 183.324 ms，检索 P50 1.714 ms、P95 2.614 ms，Recall@5 为 1.0000。

## 结论边界

- JaCoCo 的 70% instruction coverage 门禁只覆盖配置的 application 包，不代表全仓覆盖率达到 70%。
- BYOK 验证使用本地 mock，不代表任意公网 endpoint 的兼容性、可用性或性能。
- a11y 仅阻断 critical；已知 serious 对比度项未通过修改视觉 token 隐藏，不等同完整 WCAG 2 AA 合规。
- visual 是截图产物验证，不是像素差异阻断测试。
- 检索容量使用合成数据，不支持生产级 RAG、并发容量或 SLO 结论。
- 本快照不提供生产环境消息零丢失、长连接并发或多实例一致性证据。
