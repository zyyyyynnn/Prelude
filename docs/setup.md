# 环境配置指南

推荐入口是 `start-dev.bat`。运行模式差异与停止命令见 [runtime-modes.md](runtime-modes.md)。

## 1. 环境变量

```powershell
Copy-Item .\.env.example .\.env
```

`.env` 关键字段：

```env
MYSQL_HOST_PORT=13306
REDIS_HOST_PORT=16379
RABBITMQ_HOST_PORT=5672
RABBITMQ_MANAGEMENT_HOST_PORT=15672
MYSQL_ROOT_PASSWORD=root_password
JWT_SECRET=replace-with-at-least-32-bytes-jwt-secret
APP_CRYPTO_AES_SECRET=replace-with-at-least-32-bytes-aes-secret
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1/chat/completions
OPENAI_MODEL=
```

`.env` 已被 `.gitignore` 忽略，不要提交真实密钥。内置 Provider 只是兼容路径，日常建议在前端设置中使用用户级 OpenAI-compatible BYOK。

## 2. 启动入口

```powershell
# 推荐
.\start-dev.bat

# 可选
.\start-docker.bat
```

详细模式边界、底层流程与停止命令见 [runtime-modes.md](runtime-modes.md)。

## 3. 端口

默认宿主机端口避开常见本机服务端口：

| 服务 | 端口 |
| --- | --- |
| 前端 (Vite / nginx) | 5173 |
| 后端 | 8080 |
| MySQL | 13306 |
| Redis | 16379 |
| RabbitMQ | 5672 |
| RabbitMQ 管理台 | 15672 |

不建议同时运行本机 MySQL / Redis / RabbitMQ 系统服务，以免端口冲突。

## 4. 本地验收账号

`demo / 123456` 由 `data-dev.sql` 与 dev fixture 链路提供，仅用于 local/dev。

## 5. 源码级调试配置

需要直接使用 `scripts/dev/start-dev.ps1` 时：

```powershell
Copy-Item .\backend\src\main\resources\application-local.example.yml .\backend\src\main\resources\application-local.yml
Copy-Item .\frontend\.env.example .\frontend\.env.local
```

默认连接 Docker 暴露端口，详见 `application-local.example.yml` 与 `frontend/.env.example`。启动命令：

```powershell
docker compose up -d mysql redis rabbitmq
.\scripts\dev\start-dev.ps1
```
