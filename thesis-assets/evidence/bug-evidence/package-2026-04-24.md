# 真实 Bug 记录草案 2026-04-24

## 主讲 Bug 选择

| 优先级 | Bug | 选择理由 | 证据文件 |
| --- | --- | --- | --- |
| 主讲 1 | Bug 1：Demo 登录时报 Vite 代理 `ECONNREFUSED` | 根因清晰，能说明 Demo/Real 环境隔离和前端代理契约 | `thesis-assets/evidence/bug-evidence/01-demo-proxy.md` |
| 主讲 2 | Bug 2：后端启动时 MySQL 连接失败 | 工程价值高，能说明启动脚本前置校验和环境可运行性 | `thesis-assets/evidence/bug-evidence/02-mysql-preflight.md` |

Bug 3 和 Bug 4 可作为备选问答材料，不作为论文主线展开，避免 Bug 记录过多分散答辩叙事。

## Bug 1：Demo 登录时报 Vite 代理 `ECONNREFUSED`

**问题**：Demo 前端启动后，登录请求 `/api/auth/login` 代理失败。

**现象**：Vite 控制台出现 `AggregateError [ECONNREFUSED]` 和 `http proxy error: /api/auth/login`。

**排查过程**：真实模式 `8080/api/health` 与 `5173/login` 正常；Demo 模式后端目标为 `8081`，但前端缺少 `.env.demo`，导致 Vite 在缺省配置下仍把 `/api` 代理到 `http://localhost:8080`。

**解决方案**：新增 `frontend/.env.demo`，固定 `VITE_PORT=5174` 与 `VITE_PROXY_TARGET=http://127.0.0.1:8081`；启动脚本准备阶段增加 `.env.demo` 校验，缺失或端口错误时提前失败。

**教训**：Demo 与真实环境只靠启动端口区分不够，前端代理目标也必须作为环境契约显式校验，否则问题会延迟到登录阶段才暴露。

## Bug 2：后端启动时 MySQL 连接失败

**问题**：Demo 后端启动到 Hikari 初始化阶段失败。

**现象**：后端日志出现 `Communications link failure`、`Connection refused: getsockopt`，Spring Boot 初始化失败并退出。

**排查过程**：异常发生在数据源初始化阶段，不是 Controller 或业务代码错误；根因是 MySQL 服务未监听目标端口，或本地连接参数与实际 MySQL 服务不一致。

**解决方案**：保持现有启动链路不变，在启动脚本准备阶段增加 MySQL 连接检查和关键配置检查，先确认数据库服务可连接、`jwt.secret` 和 AES 密钥满足安全约束，再启动后端。

**教训**：后端启动失败不一定来自 Java 代码。数据库服务、端口、账号密码和本地配置应在脚本层提前检查，避免 Maven/Spring Boot 输出长堆栈后才发现环境未就绪。

## Bug 3：PowerShell 执行策略阻止 Demo reset 脚本运行

**问题**：执行 `.\scripts\demo\reset-demo.ps1` 被系统策略拦截。

**现象**：PowerShell 提示脚本未数字签名，当前系统禁止运行该脚本。

**排查过程**：脚本内容本身无错误，问题来自 Windows PowerShell 执行策略限制。用户本机可使用 `-ExecutionPolicy Bypass` 在当前进程绕过策略。

**解决方案**：README 与使用说明统一改为 `pwsh -ExecutionPolicy Bypass -File ...`，避免用户直接执行脚本时被策略阻断。

**教训**：Windows 环境下脚本可执行性不仅取决于脚本逻辑，还受执行策略影响。面向答辩演示的一键脚本必须给出稳定、可复制的执行命令。

## Bug 4：页面切换时出现轻微抽动

**问题**：不同页面切换时，整体布局出现轻微横向或纵向抖动。

**现象**：从登录页、主工作台、设置页等页面切换时，顶部和页面主体位置有轻微变化。

**排查过程**：不同页面内容高度不同，滚动条出现与消失会影响可视区域宽度；同时页面间 hero 区高度和布局边距不完全一致，也会放大视觉抽动。

**解决方案**：统一页面壳层间距和 hero 区布局节奏，启用稳定滚动条空间，减少不同页面之间的尺寸跳变。

**教训**：前端体验问题不一定是动画或路由问题，滚动条、页面最小高度和共享布局节奏也会造成切换抖动。全局壳层应作为统一约束管理。
