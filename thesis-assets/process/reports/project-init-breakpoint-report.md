# 工程初始化断点交付记录

## 断点范围

- 新建后端工程 `backend`
- 新建前端工程 `frontend`
- 补齐根目录 `.gitignore`
- 补齐根目录 `README.md`
- 预置后端公共配置、本地配置、数据库初始化脚本与岗位模板数据
- 预置前端路由、状态管理、基础页面、接口层与流式组合式函数骨架

## 已完成内容

### 后端

- Maven 单模块工程已建立，JDK 版本固定为 21
- Spring Boot 主启动类已可启动
- 已加入后续 MVP 所需核心依赖：
  - `spring-boot-starter-web`
  - `spring-boot-starter-validation`
  - `mybatis-plus-spring-boot3-starter`
  - `mysql-connector-j`
  - `jjwt`
  - `okhttp`
  - `pdfbox`
- 已提供 `/api/health` 健康检查接口
- 已预置：
  - `application.yml`
  - `application-local.yml`
  - `schema.sql`
  - `data.sql`

### 前端

- Vite + Vue 3 + TypeScript 工程已建立
- 已接入：
  - Vue Router
  - Pinia
  - pinia-plugin-persistedstate
  - Axios
  - Element Plus
  - markdown-it
- 已补齐：
  - `src/api/http.ts`
  - `src/router/index.ts`
  - `src/stores/user.ts`
  - `src/composables/useInterviewStream.ts`
  - `src/types/index.ts`
  - 4 个主页面骨架
  - 全局样式与环境变量文件

## 验证结果

- 后端打包：通过
- 前端构建：通过
- 后端启动验证：通过
  - 使用 `java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`
  - 启动后访问 `http://127.0.0.1:8080/api/health`，返回 `{"status":"ok"}`
- 前端启动验证：通过
  - 使用 `npm run dev -- --host 127.0.0.1 --port 5173`
  - Vite 日志显示开发服务器已在 `http://127.0.0.1:5173/` 就绪
  - 使用 HTTP 请求访问首页，返回状态码 `200`

## 当前边界说明

- 当前仅完成工程初始化断点，不包含可用的业务链路
- 后端为了允许空库状态下启动，临时排除了数据源自动配置
- 下一断点将进入“基础设施 + 认证”，届时会接通统一返回体、异常处理、JWT、拦截器、跨域与登录注册接口

## 审查重点

- 工程目录是否符合后续开发习惯
- 环境变量与敏感配置隔离方式是否接受
- 断点目标是否满足“可启动空骨架”的预期
