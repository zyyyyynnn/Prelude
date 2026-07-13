# docs 项目文档索引

本目录只放项目开发、运行、接口、产品能力、UI 质量与维护说明。临时截图、运行日志、论文过程报告和一次性审查材料不要放入 `docs/`。

## 文档入口

| 路径 | 职责 | 维护触发 |
| --- | --- | --- |
| `setup.md` | 本地环境变量、配置模板、端口与启动前置条件 | `.env`、`application-local.yml`、端口或中间件配置变化 |
| `runtime-modes.md` | `start-dev.bat` / `start-docker.bat` / `scripts/dev` 三类运行入口边界 | 启动脚本、Docker profile 或运行口径变化 |
| `api.md` | REST / SSE 接口清单，覆盖认证、LLM 配置、简历、面试和数据分析接口 | API 路径、鉴权语义或响应字段变化 |
| `byok-capability.md` | OpenAI-compatible BYOK 能力边界、接口行为与验证方式 | BYOK、模型发现、Key 保存或 fallback 语义变化 |
| `product/interview-main-flow.md` | 模拟面试主链路的产品定位、结构化报告契约与 service 边界 | 面试阶段、报告结构、评分来源或 fixture 主口径变化 |
| `architecture/overview.md` | 当前架构总览：域包、跨域 Port、数据真源、前端 feature 边界 | 域边界、Port、平台内核或前端 feature 结构变化 |
| `architecture/contributing-boundaries.md` | 增量改动的依赖方向、Port 放置、用例与前端贡献规则 | 架构门禁策略或过渡例外收缩时 |
| `quality/ui-quality-system.md` | UI 自动化质量体系当前态（`verify:ui` / `verify:tokens` / `verify:a11y` / `capture:visual` / Component Lab / CI 接入） | UI 自动化命令、Component Lab 范围、CI 接入策略变化 |
| `quality/local-review-checklist.md` | 本地预检命令与红线扫描 | CI、质量门禁或红线扫描命令变化 |
| `quality/risk-register.md` | 当前仍需跟踪的工程风险与已关闭风险索引 | 风险状态、触发条件或依赖 overrides 变化 |
| `images/` | README 长期引用的稳定截图 | 仓库门面截图需要刷新时 |

## 与其他路径的边界

| 路径 | 职责 |
| --- | --- |
| `README.md` | 仓库门面、快速开始与项目结构总览 |
| `DESIGN.md` | UI 设计规范最高入口 |
| `AGENTS.md` | Agent 协议与论文资产治理入口 |
| `docs/` | 项目开发、产品能力与维护文档 |
| `output/` | 可再生成截图、日志和自动化输出，不作为事实源 |
| `thesis-assets/` | 论文证据、图表、文献、答辩材料与正文治理资产 |

## UI 规范维护原则

- UI 规范最高入口是 `DESIGN.md`，任何样式与组件约束以 `DESIGN.md` 为准。
- UI 自动化质量体系的当前态、维护方式、CI 接入见 `quality/ui-quality-system.md`。
- 不在 `docs/` 中保留 UI 过程报告；过程审计记录在 `thesis-assets/evidence/phase-reports/ui-phase2-quality-system-2026-06.md`。

## 图片放置规则

- `docs/images/` 只放 README 长期引用的精选截图。
- 临时截图、Playwright 输出和批量截图优先放入 `output/`。
- 论文或答辩采用的截图必须在 `thesis-assets/meta/final-evidence-lock.md` 或图表登记表中登记。

## 维护原则

- 文档职责单一：接口放 `api.md`，运行放 `setup/runtime`，产品主链路放 `product/`，架构现状与贡献规则放 `architecture/`，质量放 `quality/`，UI 规则放 `DESIGN.md`。
- 不把历史 Demo Twin、已完成的迁移/重构过程稿提升为当前运行说明；过程材料只在 `thesis-assets` 归档或删除。
- 若 README、接口、运行入口、产品契约、架构边界或质量门禁变化，优先更新对应源文档，再更新索引。
