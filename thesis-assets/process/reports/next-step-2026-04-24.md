# 2026-04-24 论文下一步推进清单

## 结论

当前工程侧已经从“功能实现收尾”转入“论文证据锁版与答辩材料组织”阶段。第三章图表、第五章 Demo 业务测试、主讲 Bug 和答辩基础材料已经落档。下一步不应继续扩功能，而是审查现有证据口径，并开始组织 PPT 页级映射和讲稿。

## 当前可推进方向

1. 第三章：已补系统架构图、用例图、E-R 图 Mermaid 源文件和 PNG 导出图，图表登记已更新。
2. 第五章：已回填测试环境、后端测试、前端构建、`TC-01` 到 `TC-09` Demo 业务测试结果、SSE TTFB 和 PDF 上传/解析接口耗时。
3. 第四章：已整理 SSE 推送、PDF 解析、上下文截断三组核心代码片段。
4. Bug 记录：已整理 4 个候选真实问题，并选定 Demo 代理错连、MySQL 启动前置校验作为 2 个主讲 Bug。
5. 答辩材料：已形成演示顺序、系统亮点、关键难点和老师可能追问口径。

## 已有素材基础

- `docs/images/` 已有 README 展示截图，可作为界面预览素材基础。
- `output/demo/screenshots/` 保留 Demo 截图生成产物，可用于补充页面证据。
- `README.md` 已有 Mermaid 架构图，可作为第三章架构图的初始版本。
- `docs/api.md` 已承接核心接口清单，可用于第四章接口说明。
- `thesis-assets/final-supplement-checklist.md` 已列出终稿前最低必要补料项。

## 已补齐的真实数据

- 测试环境：已记录 Windows、PowerShell、JDK、Maven、Node.js、npm、MySQL 和浏览器版本。
- 功能测试：`TC-01` 到 `TC-09` 已在 Demo Twin 模式下通过。
- 性能数据：已记录 SSE TTFB 59 ms、SSE 总耗时 198 ms、PDF 上传/解析接口耗时 36 ms。
- 网络口径：本次为本机回环 + Demo Twin 脚本化模型数据，不代表真实公网 LLM 性能。

## 可直接整理成 Bug 包的候选问题

1. Demo 前端登录时报 `ECONNREFUSED`：`.env.demo` 缺失导致 Vite 代理错误指向 `8080`。
2. 后端启动 MySQL 连接失败：数据库服务未就绪时 Spring Boot 初始化数据源失败。
3. PowerShell 执行策略阻止脚本运行：`reset-demo.ps1` 未签名，需要用 `-ExecutionPolicy Bypass`。
4. 页面切换轻微抽动：滚动条与页面布局高度变化导致视觉抖动。

## 下一步执行建议

下一步建议先审查第五章 Demo Twin 测试口径是否满足论文要求。如果需要写真实性能对比，再切换真实模型环境采集公网 LLM 响应数据；如果不需要，则直接进入 PPT 页级映射表和正式讲稿整理。
