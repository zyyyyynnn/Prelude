# 环境配置指南

运行模式边界见 [runtime-modes.md](runtime-modes.md)。`start-real.bat`、`start-demo.bat` 和对应 `.ps1` 是 local runtime 入口，使用本机或本机可达 MySQL / Redis / RabbitMQ；Docker runtime 使用 `docker compose up -d ...`，二者不应混用同一组默认端口。

## 后端配置

复制后端配置模板：
```powershell
Copy-Item .\backend\src\main\resources\application-local.example.yml .\backend\src\main\resources\application-local.yml
```

修改 `application-local.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/interview_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_mysql_password

jwt:
  secret: replace-with-at-least-32-bytes-jwt-secret

app:
  crypto:
    aes-secret: replace-with-at-least-32-bytes-aes-secret

openai:
  base-url: ${OPENAI_BASE_URL:}
  model: ${OPENAI_MODEL:}
  api-key: ${OPENAI_API_KEY:}
```

- `application-local.yml` 已被 `.gitignore` 忽略，不要提交真实数据库密码、JWT secret、AES secret 或模型 Key。
- JWT secret 和 AES secret 必须通过本地配置或环境变量提供，避免误用默认密钥。
- 推荐在前端全局设置中使用 OpenAI-compatible BYOK 配置用户级 endpoint、API Key 与运行模型；环境变量仅作为内置 Provider 的兼容路径。

## 前端配置

复制前端配置模板：
```powershell
Copy-Item .\frontend\.env.example .\frontend\.env.local
```

默认真实版配置：
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

## 数据库

真实版数据库：
```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Demo 数据库：
```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

真实模式默认只初始化岗位模板和 LLM Provider 基础数据。Demo 模式额外加载 `data-demo.sql`，并由 `/api/demo/reset` 重建完整演示闭环。

## RabbitMQ

后端报告生成任务队列依赖 RabbitMQ。启动真实版或 Demo 版前需确保 AMQP 端口可用：

```powershell
Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq 5672 }
```

如需使用 Docker Compose 管理 RabbitMQ，应按 Docker runtime 启动整组容器服务，而不是把 local launcher 当作 Docker 入口。Redis 仅承担限流、缓存和状态辅助职责，不再承担报告任务队列。

## 端口规划

- 真实版：后端 8080 / 前端 5173
- Demo 版：后端 8081 / 前端 5174
真实版和 Demo 版的端口、数据库、前端环境与登录态相互隔离。
