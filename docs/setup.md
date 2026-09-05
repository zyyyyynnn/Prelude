# 本地开发

## 环境

- Windows 11 与 PowerShell 7+
- Java 与 Maven，具体要求见 `backend/pom.xml`
- Node.js 与 npm，具体要求见 `frontend/package.json`
- Docker Desktop

## 启动

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis rabbitmq versitygw
```

后端：

```powershell
mvn -f backend/pom.xml -Dspring-boot.run.profiles=dev spring-boot:run
```

健康检查：`http://127.0.0.1:8080/actuator/health`。

`dev` profile 会加载 `data-dev.sql`，提供 `demo / 123456`、三份匿名岗位简历、三场完整历史面试与一场进行中会话，覆盖 Java 后端、前端和算法岗位。每次开发启动只重置 `demo` 账户的验收数据，其他本地账户保持不变。

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

集成验证由 CI 与本地 Docker 基础设施共同提供环境变量：

- `PRELUDE_MYSQL_SMOKE=true`：MySQL 8.4 执行当前 Flyway baseline，并验证数据库集成契约与 `demo` 验收数据的确定性重置。
- `PRELUDE_IDENTITY_SMOKE=true`：基于真实 MySQL 与 Redis 验证注册登录、Session rotation/revoke、CSRF、Origin 与 profile revision 契约。
- `PRELUDE_S3_SMOKE=true`：通过 Testcontainers 启动 VersityGW，验证 S3 适配器契约与 Asset 生命周期。

所有 DDL 位于 `backend/src/main/resources/db/migration/`：`V20260830__establish_prelude_schema.sql` 建立当前 schema，`R__reference_data.sql` 以幂等方式维护 reference data。数据库仅含开发/demo 数据，schema 调整直接修改当前 baseline 后通过 `docker compose down -v` 空库重建验证。

OAuth（Google/GitHub）为可选能力：在 `.env` 中配置 `OAUTH_GOOGLE_CLIENT_ID`/`OAUTH_GOOGLE_CLIENT_SECRET` 与 `OAUTH_GITHUB_CLIENT_ID`/`OAUTH_GITHUB_CLIENT_SECRET` 后启用；未配置时密码登录正常启动，不要求任何 OAuth 凭据。
