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
npm --prefix frontend run check
npm --prefix frontend run verify:architecture
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
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

以上 `npm --prefix frontend run X` 均以仓库根为工作目录；在 `frontend/` 下执行时改用 `npm run X`。

集成验证由 CI 与本地 Docker 基础设施共同提供环境变量：

- `PRELUDE_MYSQL_SMOKE=true`：MySQL 8.4 执行当前 Flyway baseline，并验证数据库集成契约与 `demo` 验收数据的确定性重置。
- `PRELUDE_IDENTITY_SMOKE=true`：基于真实 MySQL 与 Redis 验证注册登录、Session rotation/revoke、CSRF、Origin 与 profile revision 契约。
- `PRELUDE_S3_SMOKE=true`：通过 Testcontainers 启动 VersityGW，验证 S3 适配器契约与 Asset 生命周期。

上述开关未设置时对应测试直接跳过，`mvn clean test` 仍会成功，因此本地跑单测默认拿不到数据库、会话与对象存储这三层保障。若本机无法拉取 Testcontainers 的 `testcontainers/ryuk` 回收镜像，追加 `TESTCONTAINERS_RYUK_DISABLED=true`：本地 `versity/versitygw` 镜像已由 `docker compose` 提供，关闭回收器不影响这两组测试的判定。

## 视觉基线

`npm --prefix frontend run verify:visual` 会按 `*-win32.png` 基线做像素比对，只在 Windows 渲染器上与 CI 一致。组件检查面按**面板**逐张比对（亮/暗各 15 张，`component-lab-<panel>-<scheme>.png`），面板清单写在测试里，新增面板未登记会先失败在标题断言上；面板高于视口时测试会先按实测差额扩窗再取图（滚动容器不揭示的像素不会被绘制），含 WebGL 品牌球的面板把该元素 mask 掉，改用几何断言。有意改变视觉时用它更新基线，不要手工改图：

```powershell
npm --prefix frontend run snapshot:update
```

`npm --prefix frontend run capture:surfaces` 生成覆盖登录深浅色、侧栏展开折叠、面试空态、上下文选择器、文字输入、语音连接/聆听/处理/播报/回退、报告、看板、设置五个分区、组件检查面与 404 的界面截图，写入仓库唯一的界面资产目录 `docs/screenshots/surfaces/`。它是随代码一起提交、供人工回归对照的界面资产，不产生断言，也不是门禁。`manifest.json` 除提交号外还记录采集时工作树是否与提交一致——截图总在提交前生成，因此 `inputsMatchRevision: false` 意味着这批图来自未提交的代码，采集时会同步告警。

语音实时链路的五帧由 `tests/demo-harness.ts` 的 `installVoiceLane` 驱动：它只假掉 `/api/ws` 传输与音频播放端，跑的是真实 `useVoiceInterview` 状态机与真实 composer。这些帧证明客户端状态与界面，不证明上游语音质量——后者只能由一次真实上游通话验证，无法在 CI 重生成。

`@demo` 链路测试的截图只作为该次运行的诊断证据，随 Playwright 报告写入 `frontend/test-results/`，不进入资产目录。

所有 DDL 位于 `backend/src/main/resources/db/migration/`：`V20260830__establish_prelude_schema.sql` 建立当前 schema，`R__reference_data.sql` 以幂等方式维护 reference data。数据库仅含开发/demo 数据，schema 调整直接修改当前 baseline 后通过 `docker compose down -v` 空库重建验证。

OAuth（Google/GitHub）为可选能力：在 `.env` 中配置 `OAUTH_GOOGLE_CLIENT_ID`/`OAUTH_GOOGLE_CLIENT_SECRET` 与 `OAUTH_GITHUB_CLIENT_ID`/`OAUTH_GITHUB_CLIENT_SECRET` 后启用；未配置时密码登录正常启动，不要求任何 OAuth 凭据。
