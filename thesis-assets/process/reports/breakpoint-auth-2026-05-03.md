# 基础设施与认证断点交付记录

## 断点范围

- 后端统一返回体
- 后端业务异常与全局异常处理
- JWT 生成、解析与拦截器
- `UserContext` 当前用户上下文
- 全局跨域配置
- SSE 异步线程池配置
- 用户注册与登录接口
- 前端 Axios 拦截器
- 前端 Pinia 登录态持久化
- 前端路由守卫
- 前端登录 / 注册页面联通认证接口

## 已完成内容

### 后端

- `Result<T>` 统一普通接口响应格式
- `BusinessException` 支持业务错误与未登录错误
- `GlobalExceptionHandler` 统一处理业务异常、参数校验异常与兜底异常
- `JwtUtil` 基于 `jjwt` 生成与解析 7 天有效期 Token
- `JwtInterceptor` 从 `Authorization: Bearer {token}` 提取用户身份
- `UserContext` 使用 `ThreadLocal<Long>` 保存当前用户 ID，并在请求结束后清理
- `WebMvcConfig` 排除 `/api/auth/**` 与 `/api/health`，其余 `/api/**` 走 JWT 拦截
- `CorsConfig` 允许 `localhost:5173` 与 `127.0.0.1:5173`
- `ThreadPoolConfig` 配置 `sse-pool-` 线程池，参数为核心线程 5、最大线程 20、队列 100
- `AuthController` 提供：
  - `POST /api/auth/register`
  - `POST /api/auth/login`
- `AuthServiceImpl` 实现：
  - 用户名唯一性校验
  - BCrypt 密码加密
  - 登录密码校验
  - 登录成功返回 JWT

### 前端

- `http.ts` 请求拦截器自动附加 `Authorization`
- `http.ts` 响应拦截器统一错误提示，遇到 `401` 清空登录态并回到登录页
- `userStore` 保存 `token` 与 `username`，并持久化到 localStorage
- 路由守卫拦截未登录访问
- 登录页支持注册、登录、表单校验、成功跳转与错误提示
- 简历页面增加退出登录入口，用于验证登录态切换

## 验证结果

- 后端编译打包：通过
  - 命令：`mvn -q -DskipTests package`
- 前端类型检查与生产构建：通过
  - 命令：`npm run build`
- MySQL 环境检查：未完全通过
  - 本机存在 `MySQL80` 服务，但当前权限无法通过 `Start-Service` 启动
  - 使用 `mysqld.exe --defaults-file=E:\DevEnv\MySQL\conf\my.ini --console` 可手动启动 MySQL 监听 3306
  - 已确认可用账号为 `root`，密码已写入本地 `application-local.yml`
- 数据库初始化：通过
  - 已创建 `interview_system`
  - `schema.sql` 已初始化 5 张核心表
  - `data.sql` 已初始化 3 条岗位模板
- 认证接口实测：通过
  - `GET /api/health` 返回 `{"status":"ok"}`
  - `POST /api/auth/register` 返回 `200`
  - `POST /api/auth/login` 返回 `200`，且返回非空 JWT
  - 重复注册同一用户名返回 `400`
  - 未带 Token 访问 `/api/interview/sessions` 返回 `401`

## 当前边界说明

- 认证断点已完成真实接口验证
- Windows 服务方式启动 MySQL 仍受权限限制；当前可通过 `mysqld.exe --defaults-file=E:\DevEnv\MySQL\conf\my.ini --console` 以前台方式启动
- 本地 `application-local.yml` 属于 `.gitignore` 范围，数据库密码仅保存在本地配置中

## 下一断点

若本断点通过审查，下一步进入“简历解析断点”：

- `resume` 实体、Mapper、Service
- PDF 文件校验与 PDFBox 文本提取
- DeepSeek 同步 JSON 结构化解析
- 简历入库与简历列表查询
- 前端上传页联调
