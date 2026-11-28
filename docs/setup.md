# 本地开发

## 环境

- Windows 11 与 PowerShell 7+
- Java 与 Maven，具体要求见 `backend/pom.xml`
- Node.js 与 npm，具体要求见 `frontend/package.json`
- Docker Desktop
- 可选：Grype 0.122.1，用于本地复现 CI 的 SBOM 漏洞门禁；本机路径为 `C:\Users\ZYN\go\bin\grype.exe`

## 启动

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d mysql redis rabbitmq versitygw
docker compose ps
```

按以下顺序启动并确认上层依赖可用：

1. MySQL、Redis、RabbitMQ、VersityGW 健康。
2. 后端启动，`http://127.0.0.1:8080/actuator/health` 可用。
3. 前端启动，`http://127.0.0.1:5173` 可访问。
4. 配置文字模型；语音另需部署级 `OPENAI_API_KEY`，按需设置 `OPENAI_BASE_URL`。语音不读取聊天 BYOK 凭据。

基础设施端口（MySQL 13306、Redis 16379、RabbitMQ 5672、S3 19000）与 app 的 8080/5173 只发布到 `127.0.0.1`：这套栈的凭据是仓库公开默认值。需要别的设备访问时，改 `docker-compose.yml` 对应映射，并同步 `S3_PUBLIC_ENDPOINT` 与 `application.yml` 的 `app.cors.allowed-origins`（默认放行 localhost/127.0.0.1:5173）。CORS 与 WebSocket 共用该显式 Origin 列表，并始终允许 same-origin；不允许 `*`。

生产部署使用 `prod` profile 时必须设置 `SESSION_COOKIE_SECURE=true`，否则启动失败。密码登录与注册默认按远端地址和端点执行 Redis 令牌桶限流；可通过 `PRELUDE_AUTH_RATE_LIMIT_ENABLED=false` 关闭，容量与速率见 `application.yml`。Redis 限流不可用时登录/注册返回 `503 rate_limit_unavailable`，前端显示服务端 detail 的系统 toast，不当作密码错误。启动探测 `/api/auth/me` 的 401 按匿名处理；网络失败、非 OK 响应或畸形数据进入 `unavailable`，保留原路由并提供重试。普通业务 API 的 401 才触发 Session 失效提示并清空账户 Query cache。

前端 Go 代理用客户端地址覆盖 `X-Forwarded-For`，清除其他转发头并设置可信的 `X-Forwarded-Proto`，compose 中的后端启用 `SERVER_FORWARD_HEADERS_STRATEGY=framework`；其他部署只能在受信任代理边界启用该配置，且不得公开后端端口，避免客户端伪造限流地址或 same-origin 判断。

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

先运行编译、类型与静态门禁，再运行测试、构建与浏览器验证。以下命令以仓库根为工作目录；在 `frontend/` 下改用 `npm run X`。

```powershell
mvn -f backend/pom.xml clean test
mvn -f backend/pom.xml -Pquality cyclonedx:makeBom
npm --prefix frontend run generate:sbom
npm --prefix frontend run licenses:notices
npm --prefix frontend run verify:licenses
npm --prefix frontend run check
npm --prefix frontend run verify:architecture
npm --prefix frontend run verify:oxc
npm --prefix frontend run verify:governance
npm --prefix frontend run verify:flaky
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run test:unit
npm --prefix frontend run test:smoke
npm --prefix frontend run verify:identity
npm --prefix frontend run build
npm --prefix frontend run verify:cascade
npm --prefix frontend run verify:production
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
npm --prefix frontend run verify:a11y
npm --prefix frontend run verify:visual
npm --prefix frontend run verify:evidence
npm --prefix frontend run capture:surfaces
npm --prefix frontend run verify:demo-copy
npm --prefix frontend run visual:diff-report
git diff --check
```

`verify:cascade` 必须在 `build` 之后。`capture:surfaces` 在提交前更新证据，提交后执行 `verify:evidence` 验证干净工作树；前端输入有未提交改动时，该门禁按设计失败。SBOM 的 Grype 扫描命令见[依赖与证据门禁](#依赖与证据门禁)。各门禁断言范围见 [UI 质量体系](quality/ui-quality-system.md)。

### CI 顺序

| Job             | 依赖                                     | 执行顺序                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| --------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `supply-chain`  | 无，可与 `backend` 并行                  | checkout → Java 21/Node 24 → `npm ci` → backend/frontend runtime SBOM → 构建前后端镜像 → Syft 生成两份 image SBOM → `licenses:notices` → `verify:licenses` → Grype 扫描四份 SBOM → 上传 SBOM、notice 与漏洞证据                                                                                                                                                                                                                                                                                            |
| `backend`       | 无，可与 `supply-chain` 并行             | 完整历史 checkout → `git diff --check <merge-base> HEAD` → Java 21 → 准备独立空库 → `mvn clean test`；MySQL/Identity/S3/Flyway smoke 均为 `true`                                                                                                                                                                                                                                                                                                                                                           |
| `frontend`      | 先要求 `supply-chain` success            | Windows checkout（完整历史）→ Vite+ Node 24 → `npm ci` → task cache → 安装锁定的 Chromium → `check` → `verify:architecture` → `verify:oxc` → `verify:governance` → `verify:flaky` → `verify:ui` → `verify:tokens` → `test:unit` → `test:smoke` → `verify:identity` → `build` → `verify:cascade` → `verify:production` → `verify:byok` → `verify:dark` → `verify:a11y` → `verify:visual` → `verify:evidence` → `capture:surfaces` → 截图差异检查 → `verify:demo-copy` → `visual:diff-report` → 上传视觉证据 |
| `release-smoke` | 等待 `supply-chain` 与 `backend` success | Linux checkout → Node 24 → `npm ci` → 安装 Chromium → 构建镜像 → Compose 启动隔离 MySQL/Redis/RabbitMQ/VersityGW/LLM stub → Playwright 真实服务主链路 → 采集 JSON/JUnit/HTML/trace 与服务日志 → 清理卷与容器 → 上传证据                                                                                                                                                                                                                                                                                    |

`frontend` 使用 `always()` 进入结果检查，但 supply-chain 非 success 时显式失败，不继续执行前端门禁。

### 依赖与证据门禁

后端 `quality` profile 使用 CycloneDX Maven Plugin 2.9.3 生成 `backend/target/bom.json`，前端使用 `@cyclonedx/cyclonedx-npm` 7.0.1 生成 `frontend/output/sbom.cdx.json`。CI 另用 Syft 1.57.2 从运行镜像生成 `backend/target/image-bom.json` 与 `frontend/output/image-sbom.cdx.json`。四份 SBOM 均为 CycloneDX 1.6；依赖 SBOM 来自 Maven/NPM resolved graph，image SBOM 另含 OS 包。

CI 对四份 SBOM 使用同一 Anchore Grype 0.122.1 扫描路径，HIGH/CRITICAL 漏洞阻断，未修复漏洞也计入；扫描步骤不使用 `continue-on-error`。依赖许可证策略覆盖前后端 resolved graph，容器门禁验证 backend/frontend NOTICE 与 paper-design 通知实际进入运行镜像。`.grype.yaml` 仅忽略 `CVE-2026-133325`：Prelude 的内嵌 Tomcat 未启用 clustering 或 TLS-PSK，受影响路径不可达；例外于 2027-02-28 到期，CI 会拒绝例外缺失到期日或已过期。Tomcat 11.0.30 发布到 Maven Central 后须重新评估并移除该例外。本机 Grype 安装在 `C:\Users\ZYN\go\bin\grype.exe`，确认版本后复现：

```powershell
$grype = "C:\Users\ZYN\go\bin\grype.exe"
& $grype version
& $grype sbom:backend/target/bom.json --fail-on high --output json --file backend/target/vulnerabilities.json
& $grype sbom:frontend/output/sbom.cdx.json --fail-on high --output json --file frontend/output/vulnerabilities.json
```

版本必须为 0.122.1；若 `grype version` 显示 `[not provided]`，用 `go version -m $grype` 核对模块行。其他工作站可替换 `$grype` 路径。

`licenses:notices` 从 runtime SBOM 生成前后端 `NOTICE.txt` 与 `frontend/output/license-report.json`，CI 以 `git diff --exit-code` 验证清单与依赖同步。`verify:licenses` 按 `frontend/scripts/license-policy.json` 检查明确允许和拒绝的许可证；CycloneDX `licenses` 数组的每个条目都必须是允许许可证或有效例外，只有 SPDX `expression` 内部使用 `OR` 语义。发布方提供多选许可时，`licenseSelections` 可按精确 PURL 版本选择一个已声明的允许许可证；版本变化会重新阻断审查。缺失或未知许可证阻断。例外必须指定精确组件 PURL、许可证、理由、批准人和到期日，通配符与永久例外无效。

`npm audit --omit=dev` 仅可作为本地辅助命令，不是 CI 门禁；CI 统一使用上述 SBOM 与 Grype 的 HIGH/CRITICAL 漏洞门禁。

`verify:evidence` 要求 `docs/screenshots/manifest.json` 存在、列出的 surface 文件存在且 SHA-256 与清单一致、`inputRevision` 等于当前前端输入的内容哈希，且工作树中的前端输入确实干净。输入哈希覆盖 `frontend/` 下已跟踪与未忽略文件，并经过 Git 过滤器规范化，不包含截图清单自身，避免提交 SHA 自指。CI 在 `capture:surfaces` 前验证已提交证据，捕获后执行 `git diff --exit-code -- docs/screenshots` 验证可复现性；visual evidence artifact 找不到文件即失败。

集成验证由环境变量开关：

- `PRELUDE_MYSQL_SMOKE=true`：MySQL 8.4 执行当前 Flyway baseline，验证数据库集成契约与 `demo` 验收数据的确定性重置。
- `PRELUDE_IDENTITY_SMOKE=true`：真实 MySQL 与 Redis 上的注册登录、Session rotation/revoke、CSRF、Origin 与 profile revision 契约。
- `PRELUDE_S3_SMOKE=true`：Testcontainers 启动 VersityGW，验证 S3 适配器契约与 Asset 生命周期。
- `PRELUDE_FLYWAY_SMOKE=true`：对独立空库执行全部 Flyway migration，并要求迁移后无 pending 版本。

`release-smoke` 使用独立 Compose project、临时卷和唯一账号前缀；LLM stub 只替代外部模型服务，MySQL、Redis、RabbitMQ、VersityGW、后端与前端均运行真实镜像。测试关闭 LLM 限流以避免确定性主链路触发 10 次/分钟限额，生产默认仍开启。

开关未设置时对应测试跳过。`PreludeApplicationTest` 无开关，加载完整应用上下文；Spring Session 在装配阶段连接 Redis，因此 `mvn clean test` 仍需要上面 `docker compose up -d` 的服务在跑。本机无法拉取 `testcontainers/ryuk` 时设置 `TESTCONTAINERS_RYUK_DISABLED=true`。

## 运行时状态与提交边界

| 机制             | 当前行为                                                                                                                     | 排障证据                                                           |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| InterviewSession | `ongoing → generating → finished`；失败/取消可回到 `ongoing`；仅 `ongoing` 接受消息                                          | `interview_session.status`、`FinishInterviewStatusRaceTest`        |
| InterviewStage   | 目录顺序为 `warmup → technical → deep_dive → closing`；`boundary_seq` 标记阶段起点，`awaiting_answer` 标记待答；配额 1/2/2/1 | `interview_stage`、`InterviewStageManagerTest`                     |
| Assessment       | 用户消息初始 PENDING，评分完成 SCORED，模型失败 UNSCORED；收尾时仍有 PENDING 会返回 `assessment_pending`                     | `interview_message.assessment_status`、judge retry 日志            |
| Retrieval        | 开面 index 与删除 invalidate 都通过 `RetrievalTransactions.afterCommit`；invalidation 失败时 scope 在重试成功前不可见        | `retrieval_indexed`、`retrieval_invalidate_*`、`retrieval_rebuilt` |
| Job publication  | 业务事务提交后才外发 RabbitMQ；未完成 publication 与过期 RUNNING 租约有界恢复                                                | `EVENT_PUBLICATION`、`background_job`、`job_attempt`、DLQ          |
| Asset 删除       | 业务引用与删除意图同事务；worker 先删对象，再按 attempt fence 删元数据；接口返回不代表物理删除已完成                         | `asset_deletion_request` 的 PENDING/RUNNING/BLOCKED 与 worker 日志 |

完整调用链与故障处理见 [onboarding.md](onboarding.md#排障入口)。

## 视觉基线与界面资产

```powershell
npm --prefix frontend run snapshot:update
```

有意改变视觉时用它更新 `*-win32.png` 基线。渲染器与 CI 一致（Windows）。基线重生成通道：

1. 失败运行把 actual/diff 帧留在 `frontend/test-results/`（CI 以 artifact 上传）。
2. `npm --prefix frontend run visual:diff-report` 把这些帧收拢到 `frontend/output/visual-diffs/`。
3. 人工核对每一帧后，在 Windows 工作站执行 `snapshot:update`，只提交核对过的基线。

CI 上传 Playwright 的 actual/diff PNG、差异报告与 `manifest.json` 作为 `visual-regression-evidence`；artifact 为空会阻断。

```powershell
npm --prefix frontend run capture:surfaces
```

生成界面参考图，写入 `docs/screenshots/<light|dark>/<interview|lab>/`（主题 × 板块），浏览器上下文固定为 `zh-CN` 与 `Asia/Shanghai`，避免本机时区改变日期文案。`manifest.json` 记录前端输入内容哈希、每个 surface 的 SHA-256 与路径约定；输入哈希、surface 集合或图像字节未变化时保留原 `capturedAt`。捕获会解码比较像素；仅最大通道差不超过 2 且差异像素占比不超过 0.1% 时保留原图，以消除 Chromium 抗锯齿噪声，manifest 的文件 SHA-256 仍严格校验。随后执行 `verify:evidence`；只有前端输入哈希匹配且工作树干净时通过。这是随代码提交的人工回归对照资产，CI 同时校验其哈希、输入一致性与捕获可复现性。

语音实时链路五帧由 `tests/demo-harness.ts` 的 `installVoiceLane` 驱动：假掉 `/api/ws` 传输、`getUserMedia`、`MediaRecorder` 与音频播放端，跑真实 `useVoiceInterview` 状态机与真实 composer。`@demo` 链路测试的截图写入 `frontend/test-results/` 作为该次运行的诊断证据。

DDL 位于 `backend/src/main/resources/db/migration/`：`V20260830__establish_prelude_schema.sql` 建立 schema，其后 `V<日期>__<语义>.sql` 增量修改，`R__reference_data.sql` 幂等维护 reference data。版本文件的编辑约定见 `docs/backend/architecture.md` 的 Persistence。彻底重来时 `docker compose down -v` 空库重建。

OAuth（Google/GitHub）为可选能力：在 `.env` 配置 `OAUTH_GOOGLE_CLIENT_ID`/`OAUTH_GOOGLE_CLIENT_SECRET` 与 `OAUTH_GITHUB_CLIENT_ID`/`OAUTH_GITHUB_CLIENT_SECRET` 后启用；未配置时密码登录正常启动。
