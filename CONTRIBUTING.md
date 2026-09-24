# 贡献指南

Prelude 接受与现有产品边界一致、由议题驱动的拉取请求。

1. 通过仓库 Issue Forms 创建或关联议题，并使用 GitHub 原生 Parent/Sub-issue、Blocked by 与 Development 关系维护议题关系；功能分支从 `main` 创建。
2. 按[本地开发文档](docs/setup.md)配置环境。涉及界面时同时遵守 [DESIGN.md](DESIGN.md)。
3. 提交前验证以 `docs/setup.md#验证` 为准，至少覆盖后端测试、前端检查与构建，以及直接相关的浏览器测试。
4. Issue 正文只维护长期有效的目标、规范、设计与验收结果；Bug 使用问题、复现、期望与必要环境描述单一可复现问题。
5. 拉取请求正文只描述最终交付、必要架构与稳定契约，并保持范围集中；验证结果由 GitHub Checks / Actions 表达，Issue 关联只由 GitHub Development 原生关系维护。

CI 以 `backend` 和 `frontend` 两个职责域验证变更；合并策略由仓库设置统一管理。
