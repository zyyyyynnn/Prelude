# 验证环境基线（2026-07-15）

| 项目 | 实测值 |
| --- | --- |
| 操作系统 | Windows 11 专业版 `10.0.26200` |
| PowerShell | `7.6.3` |
| CPU / 内存 | AMD Ryzen 9 7940H，16 logical processors；15.2 GiB RAM |
| Java | Microsoft OpenJDK `21.0.4` |
| Maven | `3.9.11` |
| Node.js / npm | `v24.15.0` / `11.6.0` |
| Vite+ | `0.2.4`（项目 package/lock 固定） |
| MySQL | Community Server/CLI `8.4.9` |
| 浏览器 | Microsoft Edge `150.0.4078.65` |
| Mermaid CLI | `11.16.0` |

## 验证方式

- 后端单元/应用测试和 JaCoCo：本机 Maven/JDK。
- 数据库：在用户目录临时初始化 MySQL 8.4 实例，执行统一 `schema.sql` / `data.sql`；测试完成后正常 shutdown 并删除临时数据目录。
- 前端静态、类型、构建和 Node 契约：项目 `node_modules` 与 lockfile。
- 前端 flows、a11y、visual：本机 Microsoft Edge；API 使用测试内 mock，除本地截图 harness 外不依赖真实后端。
- 检索容量：同一 JVM 单进程、固定 5,000 chunk 合成数据，详见 `retrieval-capacity-2026-07-15.md`。

## 环境边界

- 本记录不包含公网 LLM、真实 ASR/TTS、生产数据库、并发长连接或多实例测试。
- CI runner 与本机硬件不同；性能数字只引用本机记录，CI 只执行与机器无关的行为断言。
- 当前提交的远端门禁记录统一见 `quality-gates-2026-07-15.md`。
