# 贡献指南

Prelude 接受与现有产品边界一致、由议题驱动的拉取请求。

1. 先创建或关联议题，并从 `main` 创建独立功能分支；不要直接修改 `main`。
2. 按[本地开发文档](docs/setup.md)配置环境。涉及界面时同时遵守 [DESIGN.md](DESIGN.md)。
3. 提交前至少运行 `mvn -f backend/pom.xml clean test`、`npm --prefix frontend run check`、`npm --prefix frontend run build` 与直接相关的浏览器测试。
4. 拉取请求应说明行为或设计变化、实际验证结果、已知风险，并保持范围集中。

CI 以 `backend` 和 `frontend` 两个职责域验证变更；合并策略由仓库设置统一管理。
