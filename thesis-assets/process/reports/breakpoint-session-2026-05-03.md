# 面试会话断点交付记录

## 断点范围

- 岗位模板列表接口
- 创建面试会话接口
- 历史面试会话查询接口
- 创建会话时写入首条 `system` 消息
- 前端从简历列表发起面试
- 前端岗位选择弹窗
- 前端创建会话后跳转面试页

## 已完成内容

### 后端

- 新增 `PositionTemplate`、`InterviewSession`、`InterviewMessage` 实体
- 新增对应 MyBatis-Plus Mapper
- 新增岗位模板响应 DTO
- 新增面试启动请求与响应 DTO
- 新增历史会话列表响应 DTO
- 新增 `PositionService`：
  - 查询全部岗位模板
- 新增 `InterviewService`：
  - 校验简历归属当前用户
  - 校验岗位模板存在
  - 创建 `interview_session`，状态为 `ongoing`
  - 冗余写入 `target_position`
  - 创建首条 `interview_message`
  - 首条消息 `role=system`
  - 首条消息 `seq_num=0`
  - 查询当前用户历史会话
- 新增接口：
  - `GET /api/position/list`
  - `POST /api/interview/start`
  - `GET /api/interview/sessions`

### 前端

- 简历列表增加“开始面试”操作
- 点击后弹出岗位选择弹窗
- 自动加载岗位模板列表
- 创建面试会话后跳转 `/interview`
- 面试页展示会话编号与目标岗位

## 验证结果

- 后端编译打包：通过
  - 命令：`mvn -q -DskipTests package`
- 前端类型检查与生产构建：通过
  - 命令：`npm run build`
- 认证前置验证：通过
  - 注册测试用户成功
  - 登录返回非空 JWT
- 岗位模板列表：通过
  - `GET /api/position/list` 返回 3 条岗位模板
  - 包含 `Java 后端工程师`、`前端工程师`、`算法工程师`
- 创建面试会话：通过
  - 使用测试简历 ID 和岗位 ID 调用 `POST /api/interview/start`
  - 返回 `sessionId`
  - 返回 `targetPosition=Java 后端工程师`
- 数据库验证：通过
  - `interview_session.status=ongoing`
  - `interview_session.target_position=Java 后端工程师`
  - `interview_message` 首条记录为 `role=system`
  - `interview_message` 首条记录 `seq_num=0`
- 历史会话查询：通过
  - `GET /api/interview/sessions` 返回当前用户会话列表

## 当前边界说明

- 本断点不依赖 DeepSeek API Key
- 因上一断点合法 PDF 成功解析仍等待 DeepSeek Key，本轮接口验证使用数据库插入的测试简历作为前置数据
- 这不影响面试会话模块自身逻辑验证

## 下一断点

若本断点通过审查，下一步进入“流式对话断点”：

- `POST /api/interview/{sessionId}/chat`
- 用户消息落库
- 多轮上下文按 `seq_num` 拼装
- 超过 20 条时保留 `system` + 最近 18 条
- DeepSeek 流式调用
- `SseEmitter` 异步推送
- 前端 `fetch + ReadableStream` 打字机效果

该断点依赖真实 DeepSeek API Key，否则只能验证请求校验与错误边界。
