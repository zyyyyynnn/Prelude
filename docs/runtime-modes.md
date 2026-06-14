# 运行模式说明

## Local runtime

适用：本机或本机可达 MySQL / Redis / RabbitMQ，加上 Maven backend 与 Vite frontend。

入口：

- `start-real.bat`
- `start-demo.bat`
- `scripts/real/start-real.ps1`
- `scripts/demo/start-demo.ps1`

这些入口只检查和使用本机端口，不调用 Docker Compose。`.bat` 入口只做端口前置检查；`.ps1` 入口会通过公共 helper 尝试启动本机 MySQL，但 Redis 与 RabbitMQ 仍需提前启动或保持可达。

## Docker runtime

适用：Docker Compose 管理 MySQL / Redis / RabbitMQ / backend / frontend。

入口：

```powershell
docker compose up -d --build mysql redis rabbitmq backend frontend
```

Docker runtime 使用 `docker-compose.yml` 中的服务与端口映射。默认端口与 local runtime 相同，如本机已占用 3306、6379、5672 或 15672，应先停止对应本机服务，或通过 `MYSQL_HOST_PORT`、`REDIS_HOST_PORT`、`RABBITMQ_HOST_PORT`、`RABBITMQ_MANAGEMENT_HOST_PORT` 调整映射。

## 禁止混用

- 不要让 Docker backend 连接来源不明确的本机 MySQL / Redis / RabbitMQ。
- 不要在 Docker 容器运行时再启动本机 RabbitMQ 占用 5672。
- 不要把 local launcher 视为 Docker launcher；它们不会自动执行 `docker compose up`。
