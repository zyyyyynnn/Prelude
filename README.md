# 沉浸式模拟面试与简历诊断系统

## 项目简介

本项目是《基于大语言模型的沉浸式模拟面试与简历诊断系统》的毕业设计工程。当前已落地一期、二期、三期的核心范围：

- 一期：多 Provider 配置、用户自定义 API Key、会话级 Provider 快照。
- 二期：阶段化面试、AI 主动首问、会话回放、简历管理、用户资料设置。
- 三期：Markdown 报告评分解析、薄弱点提取、能力雷达、趋势图、薄弱点看板。

当前工程已实现：

- 用户登录 / 注册
- PDF 简历上传、文本提取与结构化解析
- 岗位化面试创建
- SSE 流式对话
- 阶段推进（warmup / technical / deep_dive / closing）
- 面试回放与 system 消息查看
- Markdown 面试评估报告生成
- Provider / 模型 / 用户 API Key 管理
- 用户邮箱 / 密码修改
- 简历管理与占用校验删除
- 数据看板（雷达图、趋势图、薄弱点）
- 共享毛玻璃顶部 Hero 栏与页面顶部居中提示
- Demo Twin：与真实版 UI 一致的独立演示运行时、独立数据库与全页面截图链路

## 技术栈

- 后端：Java 21、Spring Boot 3.2、MyBatis-Plus 3.5、MySQL 8.0、Apache PDFBox 3.0、OkHttp 4.12、jjwt 0.12、BCrypt、AES-256-GCM
- 前端：Vue 3.5、TypeScript 6、Vite 8、Element Plus 2.13、Vue Router 4、Pinia、Axios、markdown-it、ECharts
- 外部模型：DeepSeek API、OpenAI 兼容 Chat Completions 协议
- 流式方案：后端 `SseEmitter`，前端 `fetch + ReadableStream`

## 目录结构

```text
E:\Graduation project
├── DESIGN-SPEC.md                 # 前端 UI 设计规范
├── README.md
├── interview-backend              # Spring Boot 后端
├── interview-frontend             # Vue 前端
├── output
│   ├── demo
│   │   ├── manifest.md
│   │   └── screenshots
│   ├── review
│   │   └── screenshots
│   └── runtime                    # 本地运行日志（默认忽略）
├── scripts
│   ├── demo
│   │   ├── capture-demo.ps1
│   │   ├── reset-demo.ps1
│   │   └── start-demo.ps1
│   └── real
│       └── start-real.ps1
├── start-demo.bat                 # Demo Twin 双击启动入口
├── start-real.bat                 # 真实版双击启动入口
├── thesis-assets                  # 论文与测试过程材料
└── thesis-handbook                # 毕设全流程手册
```

## 本地启动

### 1. 数据库

默认数据库名为 `interview_system`。首次启动前执行：

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

若 Windows 服务无法启动，可使用本机 MySQL 前台方式：

```powershell
E:\DevEnv\MySQL\bin\mysqld.exe --defaults-file=E:\DevEnv\MySQL\conf\my.ini --console
```

### 2. 后端配置

后端敏感配置位于：

```text
interview-backend/src/main/resources/application-local.yml
```

该文件已被 `.gitignore` 忽略。至少需要配置数据库连接与 AES secret；如果要使用系统默认 DeepSeek Key，也在这里配置。

示例结构：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/interview_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_mysql_password
    driver-class-name: com.mysql.cj.jdbc.Driver

deepseek:
  api-key: your_deepseek_key

app:
  crypto:
    aes-secret: at-least-32-bytes-local-secret
```

`app.crypto.aes-secret` 必须至少 32 字节，用于加密用户自定义 API Key。真实 Key 和 secret 不应写入 README、前端代码或任何可提交文档。

### 3. 启动真实版

```powershell
.\start-real.bat
```

推荐直接双击根目录 `start-real.bat`。该脚本会检查并启动真实版 backend `8080` 与 frontend `5173`，并将日志写入：

- `output/runtime/backend-real/`
- `output/runtime/frontend-real/`

自动化场景也可继续使用：

```powershell
pwsh -File "E:\Graduation project\scripts\real\start-real.ps1"
```

## Demo Twin 本地演示

Demo Twin 用于答辩演示、录屏和 UI 审查，不使用真实 API Key，也不依赖真实简历内容。它与真实版共用同一套页面、路由、组件和样式，但运行时、数据库、登录态和截图产物完全隔离。

### 1. Demo 数据库

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 2. 启动 Demo Twin

```powershell
.\start-demo.bat
```

推荐直接双击根目录 `start-demo.bat`。该脚本会检查并启动 demo backend `8081` 与 demo frontend `5174`，并将日志写入：

- `output/runtime/backend-demo/`
- `output/runtime/frontend-demo/`

Demo 后端固定使用 `application-demo.yml` 和 `interview_demo` 数据库；Demo 前端固定使用 `.env.demo`，端口为 `5174`。

自动化场景也可继续使用：

```powershell
pwsh -File "E:\Graduation project\scripts\demo\start-demo.ps1"
```

### 3. 重置演示数据

```powershell
pwsh -File "E:\Graduation project\scripts\demo\reset-demo.ps1"
```

该接口只在 Demo 后端可用，用于重置演示用户、演示简历、演示会话、评分和薄弱点数据。

### 4. 生成全页面截图

```powershell
pwsh -File "E:\Graduation project\scripts\demo\capture-demo.ps1"
```

截图使用本机 Edge 渠道，不下载 Chromium。输出目录：

```text
E:\Graduation project\output\demo\screenshots
```

默认生成：

- `01-login.png`
- `02-register.png`
- `03-interview-empty.png`
- `04-interview-session-started.png`
- `05-interview-stage-technical.png`
- `06-interview-report.png`
- `07-replay.png`
- `08-resumes-empty.png`
- `09-resumes-filled.png`
- `10-settings-llm.png`
- `11-settings-profile.png`
- `12-analytics-empty.png`
- `13-analytics-filled.png`

截图清单输出到：

```text
E:\Graduation project\output\demo\manifest.md
```

截图脚本固定使用夹具文件 `interview-frontend/tests/fixtures/demo-resume.pdf`，并在截图前等待顶部 notice 消失，避免把瞬时提示拍进最终审查图。

## 核心接口

### 认证与基础数据

- `POST /api/auth/register`：用户注册
- `POST /api/auth/login`：用户登录并返回 JWT
- `GET /api/health`：健康检查
- `GET /api/position/list`：查询岗位模板

### LLM Provider 与用户配置

- `GET /api/llm/providers`：查询启用的 Provider 列表，无需登录
- `GET /api/user/llm-config`：查询当前用户 Provider、模型和脱敏 Key
- `PUT /api/user/llm-config`：保存当前用户 Provider、模型和 API Key
- `GET /api/user/profile`：查询当前用户资料
- `PUT /api/user/profile`：修改当前用户邮箱或密码

`PUT /api/user/llm-config` 请求体示例：

```json
{
  "providerKey": "deepseek",
  "model": "deepseek-chat",
  "apiKey": ""
}
```

约定：

- `apiKey` 非空：加密后写入数据库。
- `apiKey` 为空字符串：清空用户自定义 Key，回退系统默认 Key。
- 响应只返回 `apiKeyMasked`，不会返回明文或密文。

### 简历与面试

- `POST /api/resume/upload`：PDF 简历上传、文本提取与结构化解析
- `GET /api/resume/list`：查询当前用户简历，包含创建时间、使用次数、占用状态
- `DELETE /api/resume/{resumeId}`：删除未被面试占用的简历
- `POST /api/interview/start`：创建面试会话并快照 Provider / 模型
- `GET /api/interview/sessions`：查询历史会话，包含阶段、Provider、模型和报告摘要
- `GET /api/interview/{sessionId}/messages`：查询完整会话消息、阶段时间线和当前会话报告内容
- `POST /api/interview/{sessionId}/chat`：SSE 流式面试对话
- `POST /api/interview/{sessionId}/chat?autoStart=true`：在首轮未开始时由 AI 主动发起第一问
- `POST /api/interview/{sessionId}/stage`：推进面试阶段
- `POST /api/interview/{sessionId}/finish`：生成 Markdown 面试评估报告

### 三期数据分析

- `GET /api/analytics/radar`：最近 10 场已完成面试的三维平均分
- `GET /api/analytics/trend`：历史评分趋势
- `GET /api/analytics/weaknesses`：薄弱点聚合统计

## 前端页面

- `/login`：登录 / 注册
- `/interview`：主工作台，包含准备区、阶段面试区、历史会话与报告区
- `/interview/replay/:sessionId`：查看完整会话回放与阶段时间线
- `/resumes`：简历管理
- `/settings/llm`：Provider、模型与用户 API Key 设置
- `/settings/profile`：邮箱与密码设置
- `/analytics`：三期数据看板

## 验证命令

```powershell
cd "E:\Graduation project\interview-backend"
mvn -q test
mvn -q -DskipTests package

cd "E:\Graduation project\interview-frontend"
npm run build
```

本地接口基础验收建议：

1. 启动 MySQL、后端和前端。
2. 访问 `GET /api/llm/providers`，确认未登录可返回 DeepSeek 与 OpenAI。
3. 注册新用户并登录。
4. 在 `/settings/llm` 保存新 API Key，确认页面只显示脱敏末 4 位。
5. 在 `/settings/profile` 修改邮箱，尝试旧密码 + 新密码修改登录密码。
6. 使用真实 PDF 完成“上传简历 → 创建面试 → AI开始提问 / 发送回答 → 阶段推进 → 生成报告”的完整链路。
7. 进入 `/interview/replay/:sessionId`，确认 system 消息、user / assistant 消息和阶段时间线可见。
8. 进入 `/resumes`，确认已占用简历不可删除，未占用简历可删除。
9. 完成至少一场面试后进入 `/analytics`，确认雷达图、趋势图、薄弱点正常显示。

自动构建和接口边界验证默认不主动消耗真实模型调用。论文第五章所需的 PDF 解析耗时、SSE TTFB、报告截图和数据库截图，需要使用真实 PDF 与少量模型调用单独采集。

Demo 版额外验收建议：

1. 同时启动真实版和 Demo Twin，确认两个版本可并行打开且登录态不串扰。
2. 访问 `http://127.0.0.1:5174/login`，使用 `demo / 123456` 登录。
3. 不上传真实 PDF，直接通过占位 PDF 完成“上传简历 → 创建面试 → AI开始提问 → 阶段推进 → 生成报告”链路。
4. 确认 `/resumes`、`/settings/llm`、`/settings/profile`、`/analytics` 都能在 Demo Twin 中独立展示。
5. 执行 `pwsh -File "E:\Graduation project\scripts\demo\capture-demo.ps1"`，确认 `output/demo/screenshots/` 下 13 张截图与 `output/demo/manifest.md` 全部生成。
