# 沉浸式模拟面试与简历诊断系统

## 项目简介

本项目是《基于大语言模型的沉浸式模拟面试与简历诊断系统》的完整 MVP，实现了从用户登录、PDF 简历解析、岗位化面试创建、SSE 流式对话到 Markdown 评估报告生成的主链路。

## 技术栈

- 后端：Java 21、Spring Boot 3.2、MyBatis-Plus、MySQL 8.0、Apache PDFBox 3.0
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、markdown-it
- 外部模型：DeepSeek API，兼容 OpenAI Chat Completions 协议
- 流式方案：后端 `SseEmitter`，前端 `fetch + ReadableStream`

## 目录结构

```text
E:\Graduation project
├── interview-backend      # Spring Boot 后端
├── interview-frontend     # Vue 前端
├── thesis-assets          # 论文与测试过程材料
└── thesis-handbook        # 毕设全流程手册
```

## 本地启动

### 1. 数据库

默认数据库名为 `interview_system`，本地账号按 `root/mysql123456` 配置。首次启动前执行：

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS interview_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

若 Windows 服务无法启动，可使用本机 MySQL 前台方式：

```powershell
E:\DevEnv\MySQL\bin\mysqld.exe --defaults-file=E:\DevEnv\MySQL\conf\my.ini --console
```

### 2. 后端

```powershell
cd "E:\Graduation project\interview-backend"
mvn spring-boot:run
```

后端默认端口为 `8080`。`application.yml` 会自动导入本地忽略配置 `application-local.yml`。

### 3. 前端

```powershell
cd "E:\Graduation project\interview-frontend"
npm run dev
```

前端默认端口为 `5173`，开发环境接口基址由 `.env.development` 配置。

## 环境配置

后端敏感配置位于：

```text
interview-backend/src/main/resources/application-local.yml
```

该文件已被根目录 `.gitignore` 忽略，只用于本地运行。DeepSeek API Key 不应写入前端代码、README 或任何可提交文档。

前端环境变量位于：

```text
interview-frontend/.env.development
interview-frontend/.env.production
```

## 核心接口

- `POST /api/auth/register`：用户注册
- `POST /api/auth/login`：用户登录并返回 JWT
- `POST /api/resume/upload`：PDF 简历上传、文本提取与结构化解析
- `GET /api/resume/list`：查询当前用户简历
- `GET /api/position/list`：查询岗位模板
- `POST /api/interview/start`：创建面试会话并写入 `system` 消息
- `GET /api/interview/sessions`：查询历史会话
- `POST /api/interview/{sessionId}/chat`：SSE 流式面试对话
- `POST /api/interview/{sessionId}/finish`：生成 Markdown 面试评估报告

## 验证命令

```powershell
cd "E:\Graduation project\interview-backend"
mvn -q -DskipTests package

cd "E:\Graduation project\interview-frontend"
npm run build
```

本轮自动测试默认只验证构建、接口边界、会话状态和敏感信息隔离，不主动消耗 DeepSeek 真实模型调用。若需要完整真实链路，可在本地启动前后端后依次执行“上传 PDF → 选择岗位 → 流式问答 → 生成报告”。

## 论文截图占位

- 此处插入截图：登录注册页
- 此处插入截图：简历上传与解析结果页
- 此处插入截图：流式面试打字机页面
- 此处插入截图：评估报告页
- 此处插入截图：数据库 `interview_message` 表中 `role` 与 `seq_num` 数据
