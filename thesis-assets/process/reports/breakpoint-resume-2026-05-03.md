# 简历解析断点交付记录

## 断点范围

- 后端简历实体、Mapper、Service 与 Controller
- PDF 文件校验
- PDFBox 3 文本提取
- DeepSeek 同步结构化调用封装
- LLM JSON 反序列化与业务异常处理
- 简历入库与当前用户简历列表查询
- 前端简历上传页、解析结果展示与简历列表

## 已完成内容

### 后端

- 新增 `Resume` 实体与 `ResumeMapper`
- 新增简历上传响应、简历列表响应、项目 DTO 与 LLM 解析结果 DTO
- 新增 `LlmUtil`：
  - 使用 OkHttp 调用 DeepSeek Chat Completions
  - 非流式同步调用
  - 自动检查 `deepseek.api-key`
  - 解析 `choices[0].message.content`
- 新增 `ResumeServiceImpl`：
  - 仅接受 PDF 文件
  - 文件大小限制为 10MB
  - 使用 PDFBox `Loader.loadPDF`
  - 使用 `PDFTextStripper#setSortByPosition(true)` 改善排版提取顺序
  - 提取文本为空时返回业务错误
  - 调用 LLM 结构化提取 `skills` 与 `projects`
  - Jackson 解析失败时返回业务错误
  - 简历解析结果、原始文本和用户关系入库
- 新增 `ResumeController`：
  - `POST /api/resume/upload`
  - `GET /api/resume/list`

### 前端

- 简历上传页从占位页改为可用页面
- 支持拖拽或点击选择 PDF
- 前端限制 PDF 类型与 10MB 大小
- 上传时显示全屏 Loading：`AI 正在解析您的简历...`
- 解析成功后展示技能标签与项目卡片
- 支持刷新当前用户简历列表
- 保留退出登录入口

## 验证结果

- 后端编译打包：通过
  - 命令：`mvn -q -DskipTests package`
- 前端类型检查与生产构建：通过
  - 命令：`npm run build`
- MySQL 连接：通过
  - 使用本地 MySQL 账号可连接
  - `interview_system` 数据库可用
- 认证前置验证：通过
  - 注册测试用户成功
  - 登录返回非空 JWT
- 简历列表接口：通过
  - `GET /api/resume/list` 返回 `200`
- 非 PDF 上传边界：通过
  - 上传 `.txt` 文件返回 `400`
  - 错误信息为 `仅支持 PDF 文件`
- 合法 PDF 上传边界：部分通过
  - 使用本地 PDF 文件调用 `/api/resume/upload`
  - 已通过文件类型校验与接口链路
  - 该断点当时因 `deepseek.api-key` 仍为占位值，接口返回 `400`
  - 错误信息为 `DeepSeek API Key 未配置，请先修改 application-local.yml`

## 当前边界说明

- 代码已经具备完整简历解析链路
- 成功解析并入库依赖真实 DeepSeek API Key
- 当前不能伪造 LLM 结果，因此没有把“合法 PDF 成功解析入库”标记为通过
- 当前本地 `application-local.yml` 已写入真实 Key；为避免额外消耗模型调用，合法 PDF 成功解析实测仍等待你后续明确授权。

## 下一断点

若你批准在 DeepSeek Key 暂未配置的情况下继续，下一步可进入“面试会话断点”：

- 岗位模板列表
- 创建面试会话
- 写入首条 `system` 消息
- 查询历史面试会话

若你希望先闭环简历解析断点，则下一步应先补充 DeepSeek API Key。
