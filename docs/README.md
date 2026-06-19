# docs 项目文档索引

本目录只放项目开发、运行、接口、质量和 UI 维护说明。临时截图、运行日志、论文过程报告和一次性审查材料不要放入 `docs/`。

## 文档入口

| 路径 | 职责 | 维护触发 |
| --- | --- | --- |
| `api.md` | REST / SSE 接口清单，覆盖认证、LLM 配置、简历、面试和数据分析接口 | API 路径、鉴权语义或响应字段变化 |
| `byok-capability.md` | OpenAI-compatible BYOK 能力边界、接口行为与验证方式 | BYOK、模型发现、Key 保存或 fallback 语义变化 |
| `setup.md` | 本地环境变量、配置模板、端口和启动前置条件 | `.env`、`application-local.yml`、端口或中间件配置变化 |
| `runtime-modes.md` | `start-dev.bat`、`start-docker.bat`、`scripts/dev` 三类运行入口边界 | 启动脚本、Docker profile 或运行口径变化 |
| `quality/local-review-checklist.md` | 本地预检命令和红线扫描 | CI、质量门禁或红线扫描命令变化 |
| `quality/risk-register.md` | 当前仍需跟踪的工程风险与已关闭风险索引 | 风险状态、触发条件或依赖 overrides 变化 |
| `ui-style-convergence-maintenance.md` | UI 收敛后的维护基线，`DESIGN.md` 的执行摘要 | UI token、主题、截图或 seed 口径变化 |
| `ui-component-review-matrix.md` | UI 组件巡检矩阵 | 新增或重构前端组件时 |
| `images/` | README 展示用稳定截图 | 仓库门面截图需要刷新时 |

## 与其他路径的边界

| 路径 | 职责 |
| --- | --- |
| `README.md` | 仓库门面、快速开始和项目结构总览 |
| `DESIGN.md` | UI 设计规范最高入口 |
| `docs/` | 项目开发与维护文档 |
| `output/` | 可再生成截图、日志和自动化输出，不作为事实源 |
| `thesis-assets/` | 论文证据、图表、文献、答辩材料和正文治理资产 |

## 图片放置规则

- `docs/images/` 只放 README 长期引用的精选截图。
- 临时截图、Playwright 输出和批量截图优先放入 `output/`。
- 论文或答辩采用的截图必须在 `thesis-assets/meta/final-evidence-lock.md` 或图表登记表中登记。

## 维护原则

- 文档职责单一：接口放 `api.md`，运行放 `setup/runtime`，质量放 `quality/`，UI 规则放 `DESIGN.md` 与 UI 维护文档。
- 不把历史 Demo Twin 过程材料提升为当前运行说明；历史材料只在 `thesis-assets` 的归档或过程记录中保留。
- 若 README、接口、运行入口或质量门禁变化，优先更新对应源文档，再更新索引。
