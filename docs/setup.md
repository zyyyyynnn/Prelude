# 本地开发

## 环境

- Windows 11 与 PowerShell 7+
- Java 与 Maven，具体要求见 `backend/pom.xml`
- Node.js 与 npm，具体要求见 `frontend/package.json`
- Docker Desktop

## 启动

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis rabbitmq
```

后端：

```powershell
mvn -f backend/pom.xml spring-boot:run
```

健康检查：`http://127.0.0.1:8080/actuator/health`。

前端：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

访问 `http://127.0.0.1:5173`。`start-dev.bat` 执行相同的本地模式，`start-docker.bat` 执行完整容器模式。

## 验证

```powershell
mvn -f backend/pom.xml clean test
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run verify:architecture
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:a11y
npm --prefix frontend run verify:visual
npm --prefix frontend run build
npm --prefix frontend run test:smoke
npm --prefix frontend audit --omit=dev
git diff --check
```

MySQL 空库验证由 CI 设置 `PRELUDE_MYSQL_SMOKE=true`，在应用上下文启动时执行 Flyway 并确认 MyBatis 可连接。所有 DDL 位于 `backend/src/main/resources/db/migration/`。
