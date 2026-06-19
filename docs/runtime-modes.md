# 运行模式说明

Prelude 当前只维护三类运行入口：`start-dev.bat`、`start-docker.bat` 和 `scripts/dev`。旧 Demo Twin、`start-demo`、`start-real`、8081/5174 等口径只属于历史归档材料，不作为当前启动方式。

## 1. `start-dev.bat`（推荐）

适用：日常开发、人工验收、答辩演示。

特点：Docker 只管理基础中间件，Spring Boot 后端与 Vite 前端在本机运行，支持 Vite HMR。

底层流程：

1. `docker compose up -d mysql redis rabbitmq`
2. 启动后端：`mvn spring-boot:run`
3. 启动前端：`npm run dev`

访问入口：`http://127.0.0.1:5173`。

## 2. `start-docker.bat`（部署验证）

适用：Full Docker / 容器化交付验证。

特点：应用和中间件均在 Docker Compose 中运行；前端是 build 后的 nginx 静态产物，不支持 HMR。

等价命令：

```powershell
docker compose --profile app up -d --build
```

## 3. `scripts/dev`（源码级调试）

适用：需要分步骤控制 Maven、Vite 和日志输出时。

入口：`scripts/dev/start-dev.ps1`。详细说明见 [scripts/dev/README.md](../scripts/dev/README.md)。

## 中间件端口

中间件统一由 Docker Compose 管理。默认宿主机端口避开常见本机服务端口：

| 中间件 | 宿主机端口 |
| --- | --- |
| MySQL | 13306 |
| Redis | 16379 |
| RabbitMQ | 5672 / 15672 |

不建议同时运行本机 MySQL / Redis / RabbitMQ 系统服务，以免端口冲突。

## 停止服务

```powershell
# start-dev.bat：关闭后端/前端窗口或 Ctrl+C 后停止中间件
docker compose stop mysql redis rabbitmq

# start-docker.bat
docker compose --profile app down

# 含观测栈
docker compose --profile app --profile observability down
```
