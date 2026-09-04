# 贡献指南

Prelude 接受与现有产品边界一致、由议题驱动的拉取请求。

1. 通过仓库 Issue Forms 创建或关联议题，并使用 GitHub 原生 Parent/Sub-issue、Blocked by 与 Development 关系维护议题关系；从 `main` 创建独立功能分支，不要直接修改 `main`。
2. 按[本地开发文档](docs/setup.md)配置环境。涉及界面时同时遵守 [DESIGN.md](DESIGN.md)。
3. 提交前至少运行 `mvn -f backend/pom.xml clean test`、`npm --prefix frontend run check`、`npm --prefix frontend run build` 与直接相关的浏览器测试。
4. Issue 正文只维护长期有效的目标、规范、设计与验收结果；Bug 使用问题、复现、期望与必要环境描述单一可复现问题。
5. 拉取请求正文只描述最终交付、必要架构与稳定契约，并保持范围集中；验证结果由 GitHub Checks / Actions 表达，Issue 关系只由 GitHub Development 原生关系维护。正文禁止写入 Issue 编号或链接，也禁止 `Closes`、`Fixes`、`Resolves`、`Refs` 等关联标识。

CI 以 `backend` 和 `frontend` 两个职责域验证变更；合并策略由仓库设置统一管理。
