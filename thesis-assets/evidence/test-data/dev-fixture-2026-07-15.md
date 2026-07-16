# Local/dev Fixture 边界（2026-07-15）

| 入口 | 当前职责 |
| --- | --- |
| `data-dev.sql` | 幂等创建 `demo / 123456` 测试账号，不创建会话或业务结果 |
| `DevFixtureCatalog` | 从 `resources/demo/*.json` 读取岗位对话、报告草稿和 LLM 设置夹具 |
| `DevFixtureService.reset()` | 通过正式 repository、报告 parser/assembler 和结构化简历模型重建本地数据 |
| `POST /api/dev-fixtures/reset` | 仅 `app.dev-fixtures.enabled=true` 时注册 |
| `frontend/tests/local-screenshots.spec.ts` | 在真实本地服务上显式 reset 后采集人工验收截图 |

## 隔离规则

- `application-local.example.yml` 显式加载 `data.sql,data-dev.sql` 并启用 fixture。
- `application-prod.yml` 不加载 `data-dev.sql`，Full Docker / prod 不注册 reset API。
- 生产安全 `data.sql` 不含 `demo`、固定会话、评分或报告数据。
- fixture 复用正式结构化报告和 `ResumeDocument` 路径，不建立第二套业务 schema。

## 可支持结论

Fixture 提供确定、可重置的 local/dev 展示数据和离线 UI 验收入口。它不证明 PDF 批量解析成功率、真实模型质量、网络性能、生产数据迁移或高并发能力。
