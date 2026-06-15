# 环境配置指南

推荐运行方式为 `start-dev.bat`：Docker 管理基础中间件，本机运行 Maven 和 Vite。Full Docker / 部署验证请参阅 [runtime-modes.md](runtime-modes.md)。

## 1. 推荐：start-dev.bat

利用 Docker 管理底层中间件，应用程序在本机原生运行，支持前端 HMR 热重载。

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

`.env` 已被 `.gitignore` 忽略，不要提交真实密钥。

### 2. 启动

```powershell
.\start-dev.bat
```

脚本将自动执行以下操作：
1. 启动 Docker 中间件 (`docker compose up -d mysql redis rabbitmq`)
2. 在新窗口启动后端 (`mvn spring-boot:run`)
3. 在新窗口启动前端 (`npm run dev`)

### 3. 访问地址

## 2. 可选：start-docker.bat

用于部署验证的全量容器化。

### 启动

```powershell
.\start-docker.bat
```

> **注意**：容器内的前端是 build 后的 nginx 静态产物。如修改了前端代码，需要重新 build 才能生效，不支持 HMR 热重载。

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

## 3. Dev scripts（源码级调试）

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
  dev-fixtures:
    enabled: true
    stream-delay-ms: 18
    chunk-size: 12
```

- `application-local.yml` 已被 `.gitignore` 忽略。
- 启动前先 `docker compose up -d mysql redis rabbitmq`。
- `demo / 123456` 是 dev test account，仅用于 local/dev 验收。

### 前端配置

复制前端配置模板：

```powershell
Copy-Item .\frontend\.env.example .\frontend\.env.local
```

默认本地开发：

```env
VITE_PORT=5173
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_HOST=127.0.0.1
```

### 启动 dev

```powershell
docker compose up -d mysql redis rabbitmq
.\scripts\dev\start-dev.ps1
```

## 端口规划

可叠加观测栈：`docker compose --profile app --profile observability up -d`（Prometheus 9090 / Grafana 3000）。
