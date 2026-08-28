# Prelude

Prelude 是面向简历管理、模拟面试、训练报告与职业成长分析的模块化应用。

[![License](https://img.shields.io/github/license/zyyyyynnn/Prelude?style=flat&label=license&color=64748b)](LICENSE)

## 工程结构

```text
backend/   Java 21、Spring Boot、Spring Modulith、Flyway、MyBatis-Plus
frontend/  React、React Router、TanStack Query、Base UI、shadcn、Beautiful UI、Tailwind CSS、Vite
docs/      环境、架构与质量体系
```

后端采用 `com.prelude` 根包下的 16 个 Spring Modulith Application Modules，通过服务端 Session 认证。前端业务覆盖账号、简历管理、面试、报告、分析与设置；服务端状态由 TanStack Query 管理，导航状态由 React Router 与 URL 管理。

## 本地启动

环境要求以 `backend/pom.xml`、`frontend/package.json` 与容器编排文件为准，并需安装 Docker Desktop。

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis rabbitmq
mvn -f backend/pom.xml clean test
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend run build
```

双击 `start-dev.bat` 可启动 MySQL、本机 Spring Boot 和 Vite；`start-docker.bat` 构建并启动完整容器栈。

详细说明见 [docs/setup.md](docs/setup.md)，架构入口见 [docs/README.md](docs/README.md)，视觉规范见 [DESIGN.md](DESIGN.md)。
