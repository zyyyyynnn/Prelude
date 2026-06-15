# 运行模式说明

项目有两种运行模式。**Docker runtime 为默认且推荐方式**；dev local runtime 仅用于源码级调试，不是快速开始入口。

## Docker runtime（默认 / 推荐）

适用：开箱即用的全栈运行，无需本机 MySQL / Redis / RabbitMQ / Java / Maven / Node。

入口：

- 真实版：`.\start-real.bat`
- Demo Twin：`.\start-demo.bat`

底层执行（等价手动命令）：

```powershell
docker compose --profile real up -d --build   # 真实版
docker compose --profile demo  up -d --build   # Demo Twin
docker compose --profile real --profile observability up -d --build   # 叠加 Prometheus/Grafana
```

中间件由 Compose 管理，容器之间用服务名互连（`mysql:3306`、`redis:6379`、`rabbitmq:5672`）。宿主机暴露端口默认避开本机原生端口，避免冲突：

| 中间件   | 宿主机端口（可在 `.env` 覆盖） |
| :------ | :------------------------------ |
| MySQL   | 13306                           |
| Redis   | 16379                           |
| RabbitMQ | 5672 / 15672                    |

真实版与 Demo 版隔离：后端 `8080`/`8081`、前端 `5173`/`5174`、数据库 `interview_system`/`interview_demo`、Redis db `0`/`1`。

停止服务时注意：real 与 demo 共享同一组基础中间件（mysql/redis/rabbitmq）。并行运行时，`docker compose --profile real down` 会连带停掉共享中间件，影响 Demo 版。按需选择：

```powershell
docker compose stop backend-real frontend-real      # 仅停真实版应用层
docker compose stop backend-demo frontend-demo      # 仅停 Demo 应用层
docker compose --profile real --profile demo down   # 停全部 + 共享中间件
```

## Dev local runtime（仅开发调试）

适用：需要 IDE 断点调试后端，或 Vite HMR 调试前端。

> **不是默认入口。** 日常使用和演示请用上方的 Docker runtime。

入口（本机 Maven backend + Vite frontend，连接 Docker 暴露的中间件）：

- `scripts/real/start-real.ps1`
- `scripts/demo/start-demo.ps1`

约定：

1. 先用 Docker 起中间件（不起容器化应用层）：
   ```powershell
   docker compose --profile real up -d mysql redis rabbitmq
   ```
2. 复制 `backend/src/main/resources/application-local.example.yml` 为 `application-local.yml`，默认端口已对齐 Docker 暴露端口（13306 / 16379 / 5672）。
3. 运行对应 `.ps1`。

详见 [scripts/dev/README.md](../scripts/dev/README.md)。

## 不再推荐的方式

- **不推荐**在本机单独安装运行 MySQL84 / Redis / RabbitMQ 系统服务并占用 3306 / 6379 / 5672。Docker runtime 已自带这些中间件，本机服务与之共存会引发端口冲突。dev mode 也应复用 Docker 暴露的中间件端口。

## 禁止混用

- 不要让 Docker backend 同时又指向来源不明的本机中间件。
- 不要在 Docker 容器运行时再启动本机 RabbitMQ / Redis 占用同端口。
- 不要把 dev launcher（`.ps1`）当作默认运行入口。
