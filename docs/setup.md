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

基础设施端口（MySQL 13306、Redis 16379、RabbitMQ 5672、S3 19000）与 app 的 8080/5173 只发布到 `127.0.0.1`：这套栈的凭据是仓库公开默认值。需要别的设备访问时，改 `docker-compose.yml` 对应映射，并同步 `S3_PUBLIC_ENDPOINT` 与 `application.yml` 的 `app.cors.allowed-origins`（默认放行 localhost/127.0.0.1:5173）。

后端：

```powershell
mvn -f backend/pom.xml -Dspring-boot.run.profiles=dev spring-boot:run
```

健康检查：`http://127.0.0.1:8080/actuator/health`。

`dev` profile 加载 `data-dev.sql`，提供 `demo / 123456`、三份匿名岗位简历、三场完整历史面试与一场进行中会话，覆盖 Java 后端、前端和算法岗位。每次开发启动只重置 `demo` 账户的验收数据。

前端：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

访问 `http://127.0.0.1:5173`。`start-dev.bat` 执行本地模式，`start-docker.bat` 执行完整容器模式。

## 验证

```powershell
mvn -f backend/pom.xml clean test
npm --prefix frontend run check
npm --prefix frontend run verify:architecture
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:demo-copy
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:a11y
npm --prefix frontend run verify:visual
npm --prefix frontend run build
npm --prefix frontend run verify:cascade
npm --prefix frontend run verify:production
npm --prefix frontend run test:smoke
npm --prefix frontend audit --omit=dev
git diff --check
```

以上 `npm --prefix frontend run X` 以仓库根为工作目录；在 `frontend/` 下改用 `npm run X`。各门禁的断言范围见 `docs/quality/ui-quality-system.md`。

集成验证由环境变量开关：

- `PRELUDE_MYSQL_SMOKE=true`：MySQL 8.4 执行当前 Flyway baseline，验证数据库集成契约与 `demo` 验收数据的确定性重置。
- `PRELUDE_IDENTITY_SMOKE=true`：真实 MySQL 与 Redis 上的注册登录、Session rotation/revoke、CSRF、Origin 与 profile revision 契约。
- `PRELUDE_S3_SMOKE=true`：Testcontainers 启动 VersityGW，验证 S3 适配器契约与 Asset 生命周期。

开关未设置时对应测试跳过。`PreludeApplicationTest` 无开关，加载完整应用上下文；Spring Session 在装配阶段连接 Redis，因此 `mvn clean test` 仍需要上面 `docker compose up -d` 的服务在跑。本机无法拉取 `testcontainers/ryuk` 时设置 `TESTCONTAINERS_RYUK_DISABLED=true`。

## 视觉基线与界面资产

```powershell
npm --prefix frontend run snapshot:update
```

有意改变视觉时用它更新 `*-win32.png` 基线。渲染器与 CI 一致（Windows）。基线重生成通道：

1. 失败运行把 actual/diff 帧留在 `frontend/test-results/`（CI 以 artifact 上传）。
2. `npm --prefix frontend run visual:diff-report` 把这些帧收拢到 `frontend/output/visual-diffs/`。
3. 人工核对每一帧后，在 Windows 工作站执行 `snapshot:update`，只提交核对过的基线。

```powershell
npm --prefix frontend run capture:surfaces
```

生成登录深浅色、侧栏展开折叠、面试空态、上下文选择器、文字输入、语音六态、报告、看板、设置五个分区、组件检查面与 404 的界面截图，写入 `docs/screenshots/surfaces/`。这是随代码提交的人工回归对照资产，无自动断言。`manifest.json` 记录提交号与采集时工作树是否与提交一致（`inputsMatchRevision: false` 表示图来自未提交代码）。

语音实时链路六帧由 `tests/demo-harness.ts` 的 `installVoiceLane` 驱动：假掉 `/api/ws` 传输、`getUserMedia`、`MediaRecorder` 与音频播放端，跑真实 `useVoiceInterview` 状态机与真实 composer。`@demo` 链路测试的截图写入 `frontend/test-results/` 作为该次运行的诊断证据。

DDL 位于 `backend/src/main/resources/db/migration/`：`V20260830__establish_prelude_schema.sql` 建立 schema，其后 `V<日期>__<语义>.sql` 增量修改，`R__reference_data.sql` 幂等维护 reference data。版本文件的编辑约定见 `docs/backend/architecture.md` 的 Persistence。彻底重来时 `docker compose down -v` 空库重建。

OAuth（Google/GitHub）为可选能力：在 `.env` 配置 `OAUTH_GOOGLE_CLIENT_ID`/`OAUTH_GOOGLE_CLIENT_SECRET` 与 `OAUTH_GITHUB_CLIENT_ID`/`OAUTH_GITHUB_CLIENT_SECRET` 后启用；未配置时密码登录正常启动。
