# 环境配置指南

推荐运行方式为 **Local app runtime**：Docker 管理基础中间件，本机运行 Maven 和 Vite。全量容器化运行请参阅 [runtime-modes.md](runtime-modes.md)。

## 1. 推荐：Local App Runtime

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
.\start-real.bat   # 真实版
```

脚本将自动执行以下操作：
1. 启动 Docker 中间件 (`docker compose up -d mysql redis rabbitmq`)
2. 在新窗口启动后端 (`mvn spring-boot:run`)
3. 在新窗口启动前端 (`npm run dev`)

### 3. 访问地址

## 2. 可选：Full Docker runtime

用于部署验证的全量容器化。

### 启动

```powershell
.\start-real-docker.bat   # 真实版全 Docker
```

> **注意**：容器内的前端是 build 后的 nginx 静态产物。如修改了前端代码，需要重新 build 才能生效，不支持 HMR 热重载。

### 停止服务

```powershell
# 仅停真实版应用层（保留中间件运行）
docker compose stop backend-real frontend-real
# 停全部应用层 + 共享中间件
docker compose --profile real down
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
```

- `application-local.yml` 已被 `.gitignore` 忽略。
- 启动前先 `docker compose up -d mysql redis rabbitmq`。

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

### 启动 dev

```powershell
docker compose up -d mysql redis rabbitmq
.\scripts\real\start-real.ps1
```

## 端口规划

可叠加观测栈：`docker compose --profile real --profile observability up -d`（Prometheus 9090 / Grafana 3000）。
