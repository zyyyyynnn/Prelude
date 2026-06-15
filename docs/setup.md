# 环境配置指南

默认运行方式为 **Docker Compose 全栈**，无需本机安装 MySQL / Redis / RabbitMQ / Java / Maven / Node。运行模式边界见 [runtime-modes.md](runtime-modes.md)。

## Docker runtime（默认）

### 1. 环境变量

复制模板并按需编辑：

```powershell
Copy-Item .\.env.example .\.env
```

`.env` 关键字段：

```env
# 宿主机端口（默认避开本机原生端口）
MYSQL_HOST_PORT=13306
REDIS_HOST_PORT=16379
RABBITMQ_HOST_PORT=5672
RABBITMQ_MANAGEMENT_HOST_PORT=15672

# 中间件凭据
MYSQL_ROOT_PASSWORD=root_password

# 应用密钥（至少 32 字节，生产请替换为强随机值）
JWT_SECRET=replace-with-at-least-32-bytes-jwt-secret
APP_CRYPTO_AES_SECRET=replace-with-at-least-32-bytes-aes-secret

# 内置 LLM Provider（可选；推荐用前端 BYOK 配置用户级模型）
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1/chat/completions
OPENAI_MODEL=
```

`.env` 已被 `.gitignore` 忽略，不要提交真实数据库密码、JWT secret、AES secret 或模型 Key。

### 2. 启动

```powershell
.\start-real.bat   # 真实版
.\start-demo.bat   # Demo Twin（登录 demo / 123456）
```

底层等价于 `docker compose --profile real up -d --build` / `--profile demo`。

### 停止服务

real 与 demo 共享同一组基础中间件（mysql/redis/rabbitmq）。并行运行时，停止单一 profile 会连带停掉共享中间件，影响另一侧。按需选择：

```powershell
# 仅停真实版应用层（保留中间件与 Demo 版运行）
docker compose stop backend-real frontend-real
# 仅停 Demo 应用层
docker compose stop backend-demo frontend-demo
# 停全部应用层 + 共享中间件（real/demo 均停）
docker compose --profile real --profile demo down
```

### 3. 访问地址

| 项 | 真实版 | Demo 版 |
| :-- | :----- | :------ |
| 前端 | http://127.0.0.1:5173 | http://127.0.0.1:5174 |
| 后端 | http://127.0.0.1:8080 | http://127.0.0.1:8081 |
| 健康检查 | `/api/health` | `/api/health` |
| 数据库 | interview_system | interview_demo（首次启动自动创建） |

数据库 / 表由 Spring Boot `spring.sql.init` 自动初始化（schema.sql + data.sql），无需手动建库。Demo 版额外加载 `data-demo.sql`。

## Dev local runtime（仅开发调试）

源码级调试，需本机 JDK 21 / Maven / Node。中间件复用 Docker 暴露端口，**不单独安装本机 MySQL/Redis/RabbitMQ**。详见 [scripts/dev/README.md](../scripts/dev/README.md)。

### 后端配置

复制后端配置模板：

```powershell
Copy-Item .\backend\src\main\resources\application-local.example.yml .\backend\src\main\resources\application-local.yml
```

`application-local.yml` 默认值（端口对齐 Docker 暴露端口）：

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
```

- `application-local.yml` 已被 `.gitignore` 忽略。
- 启动前先 `docker compose --profile real up -d mysql redis rabbitmq`。

### 前端配置

复制前端配置模板：

```powershell
Copy-Item .\frontend\.env.example .\frontend\.env.local
```

默认真实版：

```env
VITE_PORT=5173
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_HOST=127.0.0.1
```

Demo 前端固定使用 `frontend/.env.demo`：

```env
VITE_PORT=5174
VITE_PROXY_TARGET=http://127.0.0.1:8081
```

### 启动 dev

```powershell
# 真实版 dev
docker compose --profile real up -d mysql redis rabbitmq
.\scripts\real\start-real.ps1

# Demo 版 dev
docker compose --profile demo up -d mysql redis rabbitmq
.\scripts\demo\start-demo.ps1
```

## 端口规划

| 项 | 真实版 | Demo 版 | 中间件宿主机端口 |
| :-- | :----- | :------ | :--------------- |
| 后端 | 8080 | 8081 | MySQL 13306 |
| 前端 | 5173 | 5174 | Redis 16379 |
| 数据库 | interview_system | interview_demo | RabbitMQ 5672 / 15672 |

真实版和 Demo 版的端口、数据库、Redis db、前端环境与登录态相互隔离。可叠加观测栈：`docker compose --profile real --profile observability up -d`（Prometheus 9090 / Grafana 3000）。
