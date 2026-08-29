<div align="center">
  <h1>Prelude</h1>
  <p>
    <strong>面向 AI 模拟面试、训练评估与职业成长分析的智能工作台。</strong>
  </p>
  <p>
    <a href="https://github.com/zyyyyynnn/Prelude/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/zyyyyynnn/Prelude/ci.yml?branch=main&amp;style=flat-square&amp;label=CI&amp;logo=githubactions&amp;logoColor=white&amp;color=334155"></a>
    <a href="LICENSE"><img alt="MIT 许可证" src="https://img.shields.io/github/license/zyyyyynnn/Prelude?style=flat-square&amp;label=%E8%AE%B8%E5%8F%AF%E8%AF%81&amp;logo=opensourceinitiative&amp;logoColor=white&amp;color=334155"></a>
  </p>
  <p>
    <img alt="Java 21" src="https://img.shields.io/badge/Java%2021-334155?style=flat-square&amp;logo=openjdk&amp;logoColor=white">
    <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-334155?style=flat-square&amp;logo=springboot&amp;logoColor=white">
    <img alt="Spring Modulith" src="https://img.shields.io/badge/Spring%20Modulith-334155?style=flat-square&amp;logo=spring&amp;logoColor=white">
    <img alt="Spring AI" src="https://img.shields.io/badge/Spring%20AI-334155?style=flat-square&amp;logo=spring&amp;logoColor=white">
  </p>
  <p>
    <img alt="React 19" src="https://img.shields.io/badge/React%2019-334155?style=flat-square&amp;logo=react&amp;logoColor=white">
    <img alt="TypeScript" src="https://img.shields.io/badge/TypeScript-334155?style=flat-square&amp;logo=typescript&amp;logoColor=white">
    <img alt="TanStack Query" src="https://img.shields.io/badge/TanStack%20Query-334155?style=flat-square&amp;logo=reactquery&amp;logoColor=white">
    <img alt="Tailwind CSS" src="https://img.shields.io/badge/Tailwind%20CSS-334155?style=flat-square&amp;logo=tailwindcss&amp;logoColor=white">
    <img alt="Vite" src="https://img.shields.io/badge/Vite-334155?style=flat-square&amp;logo=vite&amp;logoColor=white">
  </p>
  <p>
    <img alt="MySQL" src="https://img.shields.io/badge/MySQL-334155?style=flat-square&amp;logo=mysql&amp;logoColor=white">
    <img alt="Redis" src="https://img.shields.io/badge/Redis-334155?style=flat-square&amp;logo=redis&amp;logoColor=white">
    <img alt="RabbitMQ" src="https://img.shields.io/badge/RabbitMQ-334155?style=flat-square&amp;logo=rabbitmq&amp;logoColor=white">
    <img alt="Docker" src="https://img.shields.io/badge/Docker-334155?style=flat-square&amp;logo=docker&amp;logoColor=white">
  </p>
</div>

## 项目概览

Prelude 以面试为核心，将面试准备、实时问答、结构化评估和训练分析组织在一个工作区中。简历、岗位、JD 与通用附件当前作为面试上下文资源，为每场面试提供可控上下文，而不是独立的简历编辑工作流。

应用支持服务端会话、历史会话、文字与语音交互，以及按账号保存的自带密钥模型配置。当前仓库聚焦本地开发与可验证的模块化架构，不宣称公开托管或生产级软件即服务能力。

## 核心能力

- 上下文面试准备：组合简历、岗位、JD 与附件上下文。
- 文字与语音面试：提供流式文字回答与可安全释放资源的语音交互。
- 历史会话：浏览、置顶、隐藏和恢复历史面试会话。
- 结构化评估：生成并安全展示结构化训练报告。
- 训练分析：查看能力雷达、趋势和薄弱点。
- 自带密钥模型配置：配置 DeepSeek 或受支持的自定义模型协议。
- 账号与偏好：管理账号资料、主题和面试设置。

## 技术栈

| 层级 | 技术 | 职责 |
| --- | --- | --- |
| 前端 | React 19、TypeScript、React Router、TanStack Query、Base UI、Tailwind CSS、Vite | 路由、服务端状态、交互语义、设计令牌与生产构建 |
| 应用 | Java 21、Spring Boot 4.1、Spring Modulith、Spring AI、MyBatis-Plus、Flyway | HTTP 与会话边界、模块化业务用例、模型接入与持久化治理 |
| 数据与消息 | MySQL 8.4、Redis 7.4、RabbitMQ 4.1 | 业务与会话数据、实时广播、异步报告任务 |
| 运行环境 | Docker Compose | 本地基础设施与完整容器栈 |

shadcn/ui 与 Beautiful UI 仅提供已采用组件的源码结构或组合参考；本地源码、视觉语义与维护责任归 Prelude 所有。

## 架构

```text
React 客户端  →  Spring Modulith 模块化单体  →  MySQL / Redis / RabbitMQ
```

后端由 16 个 Spring Modulith 应用模块组成，模块拓扑由 `ApplicationModules.verify()` 与少量聚焦的 ArchUnit 规则验证。前端由 React Router 管理导航状态、TanStack Query 管理服务端状态，临时交互状态保留在最接近使用位置的组件中。

详细边界见[后端架构](docs/backend/architecture.md)与[前端架构](docs/frontend/architecture.md)。

## 仓库结构

```text
backend/   Spring Modulith 后端、Flyway 数据库迁移与后端测试
frontend/  React 客户端、设计令牌、浏览器测试与第三方许可声明
docs/      环境、架构与质量体系文档
.github/   CI 工作流与拉取请求模板
```

## 快速开始

### 环境要求

- Windows 11 与 PowerShell 7+
- Java 21、Maven
- Node.js 22.22+、npm 12
- Docker Desktop

### 启动基础设施

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis rabbitmq
```

### 启动应用

分别在两个 PowerShell 窗口启动后端和前端：

```powershell
mvn -f backend/pom.xml spring-boot:run
```

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

默认访问 `http://127.0.0.1:5173`，后端健康检查位于 `http://127.0.0.1:8080/actuator/health`。Windows 下也可运行 `start-dev.bat` 启动本地应用，或运行 `start-docker.bat` 构建完整容器栈。

## 验证

```powershell
mvn -f backend/pom.xml clean test
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend run build
npm --prefix frontend run test:smoke
```

CI 还会执行生产产物、自带密钥契约、暗色主题、可访问性、代表性视觉与运行时依赖审计。GitHub 仅以 `backend` 和 `frontend` 两个职责域作为必需检查项。

## 文档

| 文档 | 范围 |
| --- | --- |
| [本地开发](docs/setup.md) | 工具链、启动模式与完整验证命令 |
| [后端架构](docs/backend/architecture.md) | 应用模块、依赖边界与持久化规则 |
| [前端架构](docs/frontend/architecture.md) | React 目录、状态所有权与界面源码所有权 |
| [界面质量体系](docs/quality/ui-quality-system.md) | 设计令牌、主题、可访问性与视觉验证 |
| [设计规范](DESIGN.md) | Prelude 视觉与交互规范 |
| [贡献指南](CONTRIBUTING.md) | 分支、验证与拉取请求要求 |
| [安全策略](SECURITY.md) | 私密漏洞报告渠道与当前维护范围 |

## 许可证

Prelude 自有源码采用 [MIT License](LICENSE)。采用或打包的第三方源码与运行时组件保留各自条款；shadcn/ui、Beautiful UI 和 Paper Design Shaders 的分发许可声明位于 [frontend/public/licenses](frontend/public/licenses)。其中 Paper Design Shaders 使用 PolyForm Shield 1.0.0，不属于 Prelude 的 MIT 授权范围。
