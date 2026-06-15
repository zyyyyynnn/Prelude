<div align="center">
  <img src="frontend/src/assets/brand-logo.png" width="64" alt="Prelude">

# Prelude

_一款支持简历诊断与流式语音交互的沉浸式模拟面试平台_

![Java](https://img.shields.io/badge/Java-21-blue) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green) ![Vue](https://img.shields.io/badge/Vue-3-brightgreen) ![MySQL](https://img.shields.io/badge/MySQL-8.4-orange) ![Redis](https://img.shields.io/badge/Redis-7.0-red)
<br>
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED) ![Prometheus](https://img.shields.io/badge/Prometheus-Monitoring-E6522C) ![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker-darkred) ![WebSocket](https://img.shields.io/badge/WebSocket-Real--Time-black)

</div>

---

## 核心能力

- PDF 简历解析、岗位模板匹配、阶段化模拟面试与 Markdown 评估报告
- SSE 指数退避重连 + Resilience4j 熔断降级，提升 LLM 调用链路韧性
- 语音链路基础架构与 WebSocket 通道已搭建，真实低延迟表现仍需专项验证
- 用户级 LLM Provider 配置与 OpenAI-compatible BYOK，自定义 endpoint / API Key / 运行模型，API Key AES-256-GCM 加密
- 能力雷达图、评分趋势和薄弱点统计，面试到复盘闭环

## 系统架构

```mermaid
flowchart TB
  subgraph Client ["前端层"]
    U["用户终端"] --> FE["Vue 3 + Vite"]
  end

  subgraph Server ["服务层"]
    API["Spring Boot API"]
    WS["SSE / WS 双向流"]
  end

  subgraph Data ["数据与解析层"]
    DB[("MySQL 8.4")]
    Redis[("Redis (限流/缓存/状态辅助)")]
    RabbitMQ[("RabbitMQ (报告任务队列)")]
    PDF["PDFBox"]
  end

  subgraph Gateway ["LLM 网关层"]
    Router["LlmRouter (Resilience4j)"]
    LLM["内置 Provider / OpenAI-compatible BYOK"]
    Router -->|灾备切换| LLM
  end

  subgraph Ops ["沙箱与监控"]
    Prometheus["Prometheus"]
    Grafana["Grafana"]
    Prometheus --> Grafana
  end

  FE -->|REST| API
  FE <-->|流通信| WS
  WS <--> API

  API --> DB
  API <--> Redis
  API --> RabbitMQ
  API --> PDF
  API --> Router

  API -.-> Prometheus
```

## 快速开始

### 环境要求

| 组件    | 版本 | 备注                                  |
| :------ | :--: | :------------------------------------ |
| Windows |  11  | PowerShell 7+ 推荐                    |
| Docker  | Desktop | 必须，用于运行基础中间件              |

运行模式优先级（见 [docs/runtime-modes.md](docs/runtime-modes.md)）：
1. **推荐：start-dev.bat**（开发与人工验收入口；Docker 管中间件，本机跑 Spring Boot + Vite，支持 Vite HMR）
2. **可选：start-docker.bat**（Full Docker / 部署验证入口）
3. **Dev scripts**（仅用于 IDE 源码级调试）

### 推荐：启动 start-dev.bat（含热重载）

1. 准备环境变量：`Copy-Item .\.env.example .\.env`
2. 运行：`.\start-dev.bat`
3. 访问：`http://127.0.0.1:5173`
4. 使用 `demo / 123456` 做本地验收。

> 完整配置模板与字段说明见 [docs/setup.md](docs/setup.md)。

### 可选：启动 start-docker.bat（无热重载）

```powershell
.\start-docker.bat
```

前端为 build 后的 nginx 静态产物，修改前端代码需要 rebuild 才能生效，不支持 HMR。

### 停止服务

```powershell
# start-dev.bat
# 关闭后端/前端窗口或 Ctrl+C
docker compose stop mysql redis rabbitmq

# start-docker.bat
docker compose --profile app down

# 含观测栈
docker compose --profile app --profile observability down
```

## 技术栈

- **后端**：`Java 21` `Spring Boot 3.2` `MyBatis-Plus` `MySQL 8.4` `Redis` `RabbitMQ` `WebSocket` `Resilience4j` `PDFBox` `OkHttp` `JWT` `BCrypt` `AES-256-GCM`
- **前端**：`Vue 3` `TypeScript` `Vite` `shadcn-vue` `Tailwind CSS` `Vue Router` `Pinia` `Axios` `markdown-it` `ECharts`
- **模型**：内置 Provider 配置 + `OpenAI-compatible BYOK` 自定义接口
- **流式**：`Spring SseEmitter` `前端 fetch / ReadableStream`
- **运维**：`Docker Compose` `Prometheus & Grafana`

## 项目结构

```text
E:\Prelude
├── README.md
├── docs/                      # 公开文档、接口说明与截图
├── backend/                   # Spring Boot 后端
├── frontend/                  # Vue 前端
├── scripts/                   # dev mode 本机启动、重置和截图脚本
├── thesis-assets/             # 论文材料、交付物与答辩资产
├── thesis-handbook/           # 毕设流程手册与协作提示词
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

仓库包含 GitHub Actions 工作流（`.github/workflows/ci.yml`），在 Windows runner 上执行后端编译/测试与前端构建。

## 常见问题

- `demo / 123456` 是 dev test account，只由 local/dev 的 `data-dev.sql` 和 dev fixture 链路使用。
- JWT secret 和 AES secret 必须通过 `.env`（Docker runtime）或 `application-local.yml`（dev mode）提供，避免误用默认占位密钥。
- **中间件依赖**：不推荐使用本机 MySQL84、Redis 或 RabbitMQ 系统服务。Docker 中间件是所有运行模式的默认基础设施。
- 宿主机访问中间件默认端口已避开本机原生端口：MySQL 13306、Redis 16379、RabbitMQ 5672/15672，可用 .env 覆盖。
- CORS 允许源由 `app.cors.allowed-origins` 配置驱动，部署到其他地址时调整配置即可。
