<div align="center">
  <img src="frontend/src/shared/brand/brand-logo.png" width="96" alt="Prelude 品牌标识">
  <h1 align="center" style="border-bottom: none">Prelude</h1>
  <p><strong>AI 模拟面试、训练评估与职业成长分析工作台</strong></p>
  <p>
    <a href="LICENSE"><img alt="MIT 许可证" src="https://img.shields.io/github/license/zyyyyynnn/Prelude?style=for-the-badge&amp;label=LICENSE&amp;logo=opensourceinitiative&amp;logoColor=white&amp;labelColor=4B4A46&amp;color=786A63"></a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/-Java%2021-8D6352?style=for-the-badge&amp;logo=openjdk&amp;logoColor=white&amp;labelColor=4B4A46" alt="Java 21" title="Java 21">
    <img src="https://img.shields.io/badge/-Spring%20Boot-63765A?style=for-the-badge&amp;logo=springboot&amp;logoColor=white&amp;labelColor=4B4A46" alt="Spring Boot" title="Spring Boot">
    <img src="https://img.shields.io/badge/-Spring%20Modulith-6B7B62?style=for-the-badge&amp;logo=spring&amp;logoColor=white&amp;labelColor=4B4A46" alt="Spring Modulith" title="Spring Modulith">
    <img src="https://img.shields.io/badge/-React%2019-5E7780?style=for-the-badge&amp;logo=react&amp;logoColor=white&amp;labelColor=4B4A46" alt="React 19" title="React 19">
    <img src="https://img.shields.io/badge/-TypeScript-5B718A?style=for-the-badge&amp;logo=typescript&amp;logoColor=white&amp;labelColor=4B4A46" alt="TypeScript" title="TypeScript">
  </p>
  <p>
    <img src="https://img.shields.io/badge/-Tailwind%20CSS-557B7F?style=for-the-badge&amp;logo=tailwindcss&amp;logoColor=white&amp;labelColor=4B4A46" alt="Tailwind CSS" title="Tailwind CSS">
    <img src="https://img.shields.io/badge/-MySQL-516F80?style=for-the-badge&amp;logo=mysql&amp;logoColor=white&amp;labelColor=4B4A46" alt="MySQL" title="MySQL">
    <img src="https://img.shields.io/badge/-Redis-9A615A?style=for-the-badge&amp;logo=redis&amp;logoColor=white&amp;labelColor=4B4A46" alt="Redis" title="Redis">
    <img src="https://img.shields.io/badge/-RabbitMQ-936545?style=for-the-badge&amp;logo=rabbitmq&amp;logoColor=white&amp;labelColor=4B4A46" alt="RabbitMQ" title="RabbitMQ">
    <img src="https://img.shields.io/badge/-Docker-4F708F?style=for-the-badge&amp;logo=docker&amp;logoColor=white&amp;labelColor=4B4A46" alt="Docker" title="Docker">
  </p>
</div>

## 项目概览

Prelude 以面试为核心，将面试准备、实时问答、结构化评估和训练分析组织在一个工作区中。简历、岗位、职位描述与通用附件作为面试上下文资源，为每场面试提供可控上下文，而不是独立的简历编辑工作流。

应用支持服务端会话、历史会话、文字与语音交互，以及按账号保存的自带密钥模型配置。当前仓库聚焦本地开发与可验证的模块化架构，不宣称公开托管或生产级软件即服务能力。

## 核心能力

- 上下文面试准备：组合简历、岗位、职位描述与附件上下文。
- 文字与语音面试：提供流式文字回答与可安全释放资源的语音交互。
- 历史会话：浏览、置顶、隐藏和恢复历史面试会话。
- 结构化评估：生成并安全展示结构化训练报告。
- 训练分析：查看能力雷达、趋势和薄弱点。
- 自带密钥模型配置：配置 DeepSeek 或受支持的自定义模型协议。
- 账号与偏好：管理账号资料、主题和面试设置。

## 系统架构

<p align="center">
  <img src="docs/assets/architecture-overview.svg" width="960" alt="Prelude 高层系统架构总览">
</p>

README 只展示系统职责分层；16 个应用模块的完整边界与依赖拓扑见[后端架构](docs/backend/architecture.md)。前端状态所有权与目录边界见[前端架构](docs/frontend/architecture.md)。

## 技术栈

| 层级 | 技术 | 职责 |
| --- | --- | --- |
| 前端应用 | React 19、React Router、TypeScript、Vite | 页面组合、路由导航、类型契约和生产构建 |
| 界面基础 | Base UI、Tailwind CSS | 浮层、焦点与键盘交互原语，以及设计令牌和布局样式 |
| 服务端状态 | TanStack Query | 请求生命周期、缓存、失效与乐观更新 |
| 模块化后端 | Java 21、Spring Boot 4.1、Spring Modulith | 接口边界、模块化业务用例与依赖拓扑验证 |
| 模型与数据访问 | Spring AI、MyBatis-Plus | 模型协议接入、模型网关与持久化适配 |
| 持久化 | MySQL 8.4、Flyway | 业务与会话数据，以及数据库版本治理 |
| 实时与消息 | Redis 7.4、RabbitMQ 4.1 | 实时发布与订阅，以及异步报告任务 |
| 训练分析 | ECharts | 能力雷达、分数趋势与薄弱点呈现 |
| 本地运行 | Docker Compose | 本地基础设施与完整容器栈 |

shadcn/ui 与 Beautiful UI 仅提供已采用组件的源码结构或组合参考；本地源码、视觉语义与维护责任归 Prelude 所有。

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

## 仓库结构

```text
backend/   Spring Modulith 后端、Flyway 数据库迁移与后端测试
frontend/  React 客户端、设计令牌、浏览器测试与第三方许可声明
docs/      环境、架构、质量体系与产品文档资产
.github/   CI 工作流与拉取请求模板
```

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
