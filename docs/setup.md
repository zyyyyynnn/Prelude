# 环境配置指南

推荐入口是 `start-dev.bat`：Docker 管理 MySQL、Redis、RabbitMQ，本机运行 Spring Boot 和 Vite。运行模式差异见 [runtime-modes.md](runtime-modes.md)。

## 1. 环境变量

复制根目录模板：

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

## 2. 推荐启动

```powershell
.\start-dev.bat
```

访问地址：

| 服务 | 地址 |
| --- | --- |
| 前端 | `http://127.0.0.1:5173` |
| 后端健康检查 | `http://127.0.0.1:8080/api/health` |
| RabbitMQ 管理台 | `http://127.0.0.1:15672` |

本地验收账号 `demo / 123456` 由 `data-dev.sql` 和 dev fixture 链路提供，仅用于 local/dev。

## 3. 源码级调试配置

需要直接使用 `scripts/dev/start-dev.ps1` 时，先复制后端本地配置：

```powershell
Copy-Item .\backend\src\main\resources\application-local.example.yml .\backend\src\main\resources\application-local.yml
```

默认连接 Docker 暴露端口：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:13306/interview_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root_password
  data:
    redis:
      host: 127.0.0.1
      port: 16379
  rabbitmq:
    host: 127.0.0.1
    port: 5672

jwt:
  secret: replace-with-at-least-32-bytes-jwt-secret

app:
  crypto:
    aes-secret: replace-with-at-least-32-bytes-aes-secret
  dev-fixtures:
    enabled: true
    stream-delay-ms: 18
    chunk-size: 12
```

前端本地配置：

```powershell
Copy-Item .\frontend\.env.example .\frontend\.env.local
```

```env
VITE_PORT=5173
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_HOST=127.0.0.1
```

启动源码级调试：

```powershell
docker compose up -d mysql redis rabbitmq
.\scripts\dev\start-dev.ps1
```

## 4. 停止与观测栈

停止命令见 [runtime-modes.md](runtime-modes.md)。如需观测栈，可额外启用：

```powershell
docker compose --profile app --profile observability up -d
```

Prometheus 默认 `9090`，Grafana 默认 `3000`。
