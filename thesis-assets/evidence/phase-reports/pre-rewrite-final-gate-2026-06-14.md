# 正文改写前最终闸门记录（2026-06-14）

> 注：本文档记录当时阶段的 Demo Twin / 双轨运行状态。当前版本已收敛为 start-dev + start-docker，并将演示数据改为 dev fixture。

## 1. 范围

本记录用于正文改写前复审，不属于正文，不生成 DOCX/PDF，不修改 `thesis-assets/chapters/*.md`。

## 2. 本地启动脚本修复

- 复现根因：后端启动首先失败于本地未跟踪 `application-local.yml` 中历史 API Key 环境变量无默认值占位符。
- 本地处理：未跟踪本地配置改为允许空值，不写入仓库。
- 仓库处理：`start-demo.bat`、`start-real.bat`、`scripts/demo/start-demo.ps1`、`scripts/real/start-real.ps1` 增加 RabbitMQ 5672 前置检测，避免 RabbitMQ 未启动时进入较晚的 AMQP 连接失败。

## 3. 论文资产噪音整理

- 修复答辩讲稿中“Redis 用于限流和异步任务队列”的旧口径。
- 删除旧 RabbitMQ 实施计划，当前事实以 `rabbitmq-report-queue-2026-06-13.md` 与 `final-evidence-lock.md` 为准。
- 清理证据层中过强的“彻底”“企业级”“生产级可靠”等非证据化措辞。

## 4. Docker Compose 真实 API Key 功能链路

- 运行口径：`APP_DEMO_ENABLED=false`。
- API Key：由用户环境变量临时注入，未写入仓库，未记录明文。
- Endpoint 与运行模型：由环境变量临时注入；具体模型仅为本轮运行参数，不作为仓库默认配置或论文固定推荐模型。
- 结果：
  - `/finish` 返回 `status=generating` 与 `jobId=cdbf8ae0-b058-44d1-b6d5-186b7b927baa`；
  - RabbitMQ 发布与消费日志均出现；
  - ReportJobWorker 完成真实 LLM 报告生成；
  - 后端日志广播 `report_ready`；
  - `interview_session.status=finished`；
  - `summary_report` 长度为 2724；
  - RabbitMQ 队列最终 `messages=0`，`consumers=1`。

## 5. 限制

- 本记录只证明当前网络与运行模型条件下的一次真实 API Key 功能链路可用。
- 不代表公网性能基准。
- 不代表高并发压测。
- 不证明生产级可靠投递。
- 不证明消息零丢失。
