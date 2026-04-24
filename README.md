# 沉浸式模拟面试与简历诊断系统

基于大语言模型的毕业设计项目，覆盖简历解析、阶段化模拟面试、会话回放、报告生成和能力分析。项目提供真实运行模式和 Demo Twin 演示模式，便于本地开发、答辩演示与截图验收。

## 功能概览

- 用户注册、登录与 JWT 鉴权
- PDF 简历上传、文本提取与结构化解析
- 岗位模板选择与面试会话创建
- SSE 流式问答与阶段推进
- 面试回放、阶段时间线与消息记录查看
- Markdown 面试评估报告生成
- 能力雷达、评分趋势与薄弱点统计
- Provider、模型与用户 API Key 配置
- 用户邮箱与密码维护
- Demo Twin 独立数据库、独立端口和完整演示数据

## 技术栈

- 后端：Java 21、Spring Boot 3.2、MyBatis-Plus、MySQL 8.0、PDFBox、OkHttp、JWT、BCrypt、AES-256-GCM
- 前端：Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia、Axios、markdown-it、ECharts
- 模型接口：DeepSeek API、OpenAI 兼容 Chat Completions 协议
- 流式通信：Spring `SseEmitter` + 前端 `fetch` / `ReadableStream`

## 目录结构

```text
E:\graduation-project
├── README.md
├── DESIGN-SPEC.md
├── interview-backend          # Spring Boot 后端
├── interview-frontend         # Vue 前端
├── scripts                    # 辅助脚本
│   ├── demo
│   └── real
├── output                     # 截图、运行产物与审查材料
├── thesis-assets              # 论文材料
├── thesis-handbook            # 毕设手册
├── start-real.bat             # 真实版双击启动入口
└── start-demo.bat             # Demo Twin 双击启动入口
```

## 环境要求

- Windows 11
- PowerShell 7+
- Java 21
- Maven 3.9+
- Node.js 20+ 与 npm
- MySQL 8.0

启动脚本不会启动 MySQL，也不依赖 Redis。运行前需要先确认 MySQL 已启动，并已创建对应数据库。

## 配置

### 真实版数据库

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

真实版本地敏感配置放在：

```text
interview-backend/src/main/resources/application-local.yml
```

示例：

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

`application-local.yml` 已在 `.gitignore` 中忽略，不要提交真实数据库密码、模型 Key 或 AES secret。

### Demo 数据库

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Demo 前端固定使用 `interview-frontend/.env.demo`：

```env
VITE_PORT=5174
VITE_PROXY_TARGET=http://127.0.0.1:8081
```

## 本地启动

### 真实版

```powershell
.\start-real.bat
```

启动结果：

- 后端：`http://127.0.0.1:8080`
- 前端：`http://127.0.0.1:5173`
- 健康检查：`http://127.0.0.1:8080/api/health`

### Demo Twin

```powershell
.\start-demo.bat
```

启动结果：

- 后端：`http://127.0.0.1:8081`
- 前端：`http://127.0.0.1:5174`
- 健康检查：`http://127.0.0.1:8081/api/health`

Demo 模式使用 `application-demo.yml`、`interview_demo` 数据库和 `.env.demo`。它与真实版端口、数据库和登录态隔离。

## Demo 数据

重置演示数据：

```powershell
pwsh -File .\scripts\demo\reset-demo.ps1
```

`/api/demo/reset` 会重建完整演示闭环，包括演示账号、默认 LLM 配置、演示简历、进行中会话、已完成会话、回放、报告、评分历史和薄弱点数据。

默认演示账号：

```text
demo / 123456
```

生成 Demo 截图：

```powershell
pwsh -File .\scripts\demo\capture-demo.ps1
```

输出目录：

```text
output\demo\screenshots
```

默认截图：

- `01-login.png`
- `02-register.png`
- `03-interview-workbench.png`
- `04-interview-stage-technical.png`
- `05-interview-stage-deep-dive.png`
- `06-interview-report.png`
- `07-replay.png`
- `08-resumes-filled.png`
- `09-settings-llm.png`
- `10-settings-profile.png`
- `11-analytics-filled.png`

截图清单输出到：

```text
output\demo\manifest.md
```

## 主要页面

- `/login`：登录 / 注册
- `/interview`：主工作台
- `/interview/replay/:sessionId`：会话回放
- `/resumes`：简历管理
- `/analytics`：数据看板
- `/settings/llm`：LLM 配置
- `/settings/profile`：用户设置

## 核心接口

### 认证与基础数据

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/health`
- `GET /api/position/list`

### LLM 与用户设置

- `GET /api/llm/providers`
- `GET /api/user/llm-config`
- `PUT /api/user/llm-config`
- `GET /api/user/profile`
- `PUT /api/user/profile`

`PUT /api/user/llm-config` 约定：

- `apiKey` 非空：加密保存用户自定义 Key
- `apiKey` 为空字符串：清空用户自定义 Key
- 响应只返回 `apiKeyMasked`

### 简历与面试

- `POST /api/resume/upload`
- `GET /api/resume/list`
- `DELETE /api/resume/{resumeId}`
- `POST /api/interview/start`
- `GET /api/interview/sessions`
- `GET /api/interview/{sessionId}/messages`
- `POST /api/interview/{sessionId}/chat`
- `POST /api/interview/{sessionId}/stage`
- `POST /api/interview/{sessionId}/finish`

### 数据分析

- `GET /api/analytics/radar`
- `GET /api/analytics/trend`
- `GET /api/analytics/weaknesses`

## 验证命令

后端编译：

```powershell
cd .\interview-backend
mvn -q -DskipTests compile
```

前端构建：

```powershell
cd .\interview-frontend
npm run build
```

Demo 链路验收：

```powershell
.\start-demo.bat
pwsh -File .\scripts\demo\reset-demo.ps1
pwsh -File .\scripts\demo\capture-demo.ps1
```

建议按以下路径手动检查：

1. 登录 `demo / 123456`
2. 进入 `/interview` 创建或继续一场面试
3. 发送回答、推进阶段并生成报告
4. 查看 `/interview/replay/:sessionId`
5. 查看 `/resumes`、`/analytics`、`/settings/llm`、`/settings/profile`

## 注意事项

- 真实版与 Demo 版数据库不同，不要混用数据。
- 真实 API Key 只放在本地配置或用户设置中，不要写入前端代码、README 或截图材料。
- 双击启动脚本更接近手动启动方式：分别打开后端和前端命令窗口，停止服务时关闭窗口或按 `Ctrl+C`。
