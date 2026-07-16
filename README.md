<div align="center">
  <img src="frontend/src/shared/brand/brand-logo.png" width="64" alt="Prelude">

# Prelude

_一套以简历、面试证据和持续改进为主线的求职训练系统_

![Java](https://img.shields.io/badge/Java-21-blue) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green) ![Vue](https://img.shields.io/badge/Vue-3-brightgreen) ![MySQL](https://img.shields.io/badge/MySQL-8.4-orange) ![Redis](https://img.shields.io/badge/Redis-7.0-red) [![License](https://img.shields.io/github/license/zyyyyynnn/Prelude?style=flat&label=license&color=64748b)](LICENSE)
<br>
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED) ![Prometheus](https://img.shields.io/badge/Prometheus-Monitoring-E6522C) ![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker-darkred) ![WebSocket](https://img.shields.io/badge/WebSocket-Real--Time-black)

</div>

---

## 核心能力

- PDF 简历解析与结构化编辑；报告建议需逐项确认，并通过版本检查写回简历
- SSE 指数退避重连 + Resilience4j 熔断降级，提升 LLM 调用链路韧性
- 语音链路基础架构与 WebSocket 通道已搭建，真实低延迟表现仍需专项验证
- 用户级 BYOK：三类明确协议、API Key AES-256-GCM 加密，以及生产默认拒绝内网目标的出站策略
- 持久化 chunk/embedding 的关键词与向量融合检索，Embedding 故障时退化到关键词检索
- 异步结构化报告、能力画像与简历改进，形成再次训练所需的闭环

## 系统架构

```mermaid
flowchart TB
  User["用户终端"]
  Frontend["前端层：Vue 3 + Vite"]
  Backend["服务层：Spring Boot API / SSE / WebSocket"]
  Data["数据与解析层：MySQL / Redis / RabbitMQ / PDFBox"]
  Gateway["LLM 网关层：Resilience4j / BYOK"]
  Observability["监控层：Prometheus / Grafana"]

  User --> Frontend
  Frontend -->|REST / 流通信| Backend
  Backend --> Data
  Backend --> Gateway
  Backend -.-> Observability
```

## 快速开始

### 环境要求

| 组件 | 版本 | 备注 |
| :-- | :-: | :-- |
| Windows | 11 | PowerShell 7+ 推荐 |
| Docker | Desktop | 用于运行基础中间件 |

### 启动入口

- **推荐**：`.\start-dev.bat`（开发、人工验收、答辩演示；Vite HMR 可用）
- **可选**：`.\start-docker.bat`（Full Docker / 部署验证）

环境配置见 [docs/setup.md](docs/setup.md)，运行模式差异见 [docs/runtime-modes.md](docs/runtime-modes.md)。

### 常用地址

| 服务 | 地址 |
| :-- | :-- |
| 前端 | `http://127.0.0.1:5173` |
| 后端健康检查 | `http://127.0.0.1:8080/api/health` |
| RabbitMQ 管理台 | `http://127.0.0.1:15672` |

本地验收账号 `demo / 123456` 来自 `data-dev.sql` 与 dev fixture 链路，仅用于 local/dev。

## 技术栈

- **后端**：`Java 21` `Spring Boot 3.2` `MyBatis-Plus` `MySQL 8.4` `Redis` `RabbitMQ` `WebSocket` `Resilience4j` `PDFBox` `OkHttp` `JWT` `BCrypt` `AES-256-GCM`
- **前端**：`Vue 3` `TypeScript` `Vite` `shadcn-vue` `Tailwind CSS` `Vue Router` `Pinia` `Axios` `markdown-it` `ECharts`
- **模型**：默认 DeepSeek Provider + OpenAI Responses / Chat Completions / Anthropic Messages BYOK
- **流式**：`Spring SseEmitter` `前端 fetch / ReadableStream`
- **运维**：`Docker Compose` `Prometheus & Grafana`

## 项目结构

```text
E:\Prelude
├── README.md
├── DESIGN.md                  # UI 设计规范与样式维护基线
├── docs/                      # 公开文档、接口说明与截图
├── backend/                   # Spring Boot 后端
├── frontend/                  # Vue 前端
│   └── src/
│       ├── app/               # 启动、路由与运行时装配
│       ├── features/          # 业务域模块
│       ├── shared/            # 通用能力与 UI primitive
│       └── devtools/          # 仅开发态工具
├── scripts/                   # dev mode 本机启动、重置和截图脚本
├── thesis-assets/             # 论文正文、证据、文献与答辩资产
├── output/                    # dev 截图、验证产物与可复现输出记录
├── start-dev.bat              # 开发与人工验收入口（含 Vite HMR）
├── start-docker.bat           # Full Docker / 部署验证入口
```

## 页面与路由

| 路径                   | 说明                           |
| :--------------------- | :----------------------------- |
| <kbd>/login</kbd>      | 登录 / 注册                    |
| <kbd>/interview</kbd>  | 主工作台（面试对话、报告预览） |
| <kbd>/resumes</kbd>    | 简历管理                       |
| <kbd>/analytics</kbd>  | 数据看板（能力雷达、评分趋势） |

LLM 配置与用户设置已整合至全局设置弹窗（齿轮图标触发）。

### 界面预览

<div align="center">
  <img width="800" src="docs/images/login.png" alt="登录界面">
  <br><br>
  <img width="800" src="docs/images/interview-empty.png" alt="工作台首页">
  <br><br>
  <img width="800" src="docs/images/interview-chat.png" alt="流式面试交互">
  <br><br>
  <img width="800" src="docs/images/interview-report.png" alt="面试评估报告">
  <br><br>
  <img width="800" src="docs/images/resumes.png" alt="简历解析与管理">
  <br><br>
  <img width="800" src="docs/images/analytics.png" alt="能力分析看板">
</div>

## API

完整接口说明见 [docs/api.md](docs/api.md)。

## CI

仓库包含 GitHub Actions 工作流（`.github/workflows/ci.yml`）：Linux runner 使用 MySQL 8.4 验证统一 schema、生产安全数据与旧值升级，Windows runner 执行后端编译/测试，以及前端架构、契约、构建、关键流和 UI 质量门禁。详见 [docs/backend/architecture.md](docs/backend/architecture.md)、[docs/frontend/architecture.md](docs/frontend/architecture.md) 与 [docs/quality/local-review-checklist.md](docs/quality/local-review-checklist.md)。

## 关键文档

- 设计与样式：[DESIGN.md](DESIGN.md)
- 运行入口：[docs/runtime-modes.md](docs/runtime-modes.md)
- 本地配置：[docs/setup.md](docs/setup.md)
- 后端架构：[docs/backend/architecture.md](docs/backend/architecture.md)
- 前端架构：[docs/frontend/architecture.md](docs/frontend/architecture.md)
- UI 自动化质量体系：[docs/quality/ui-quality-system.md](docs/quality/ui-quality-system.md)
- 工程风险台账：[docs/quality/risk-register.md](docs/quality/risk-register.md)
- Agent 协议：[AGENTS.md](AGENTS.md)
- 论文资产索引：[thesis-assets/README.md](thesis-assets/README.md)

## 常见问题

- `demo / 123456` 是 dev test account，只由 local/dev 的 `data-dev.sql` 和 dev fixture 链路使用。
- JWT secret 和 AES secret 必须通过 `.env`（Docker runtime）或 `application-local.yml`（dev mode）提供，避免误用默认占位密钥。
- **中间件依赖**：不推荐使用本机 MySQL84、Redis 或 RabbitMQ 系统服务。Docker 中间件是所有运行模式的默认基础设施。
- CORS 允许源由 `app.cors.allowed-origins` 配置驱动，部署到其他地址时调整配置即可。

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE) for details.
