# RabbitMQ 报告任务队列集成实现证据 2026-06-13

> 状态：historical supplement。记录 2026-06-13 前后的 RabbitMQ 接入与本地联调事实。
> 当前结束面试与任务发布实现以 `finish-job-async-report-2026-07-13.md` 为准。
> 本文中的 `InterviewServiceImpl.finish(...)`、旧测试路径与直接 `RabbitTemplate` 生产者表述均已过期，正文不得直接引用。

> 历史状态说明：本文记录当时阶段的 Demo Twin / 双轨运行状态。当前版本已收敛为 start-dev + start-docker，并将演示数据改为 dev fixture。

> 本文件仅保留当时 RabbitMQ 作为报告生成异步任务队列的接入与 Docker Compose 联调证据。
> 本轮不引入死信队列、outbox、publisher confirm、并发调优。
> 答辩材料和正文引用前，必须先核对当前 finish/job 证据与 RabbitMQ 口径限制。

---

## 证据 14：RabbitMQ 报告任务队列集成（Producer / Consumer / Config）

### 14.1 集成范围
- **依赖**：`spring-boot-starter-amqp`（与 `spring-boot-starter-data-redis` 并存）。
- **基础设施**：`docker-compose.yml` 新增 `rabbitmq:3-management` 服务，端口 `5672`（AMQP）与 `15672`（管理端）；数据卷 `rabbitmq-data`；后端 `depends_on` 健康检查。
- **生产者（历史）**：当时由 `InterviewServiceImpl.finish(...)` 直接 `RabbitTemplate.convertAndSend(...)` 推送任务。当前已改为 `FinishInterview` → `JobSchedulerPort` → `RabbitJobScheduler`。
- **消费者**：`ReportJobWorker` 使用 `@RabbitListener(queues = REPORT_QUEUE)`；`/finish` 响应字段（`status="generating"` + `jobId`）保持兼容。
- **幂等保护**：`STATUS_GENERATING` 状态守卫；当前幂等与重试细节见 `JobExecutionStore` / `ReportGenerateHandler`。
- **Redis 角色回归**：限流、评分锁等仍保留；报告队列职责迁出 Redis。

### 14.2 关键实现点

#### A. 配置与 Bean

来源文件：
- `backend/src/main/java/com/interview/config/RabbitMqConfig.java`
- `backend/src/main/resources/application.yml`
- `docker-compose.yml`

```java
// RabbitMqConfig.java
public static final String REPORT_EXCHANGE = "prelude.report.exchange";
public static final String REPORT_QUEUE    = "prelude.report.generate.queue";
public static final String REPORT_ROUTING_KEY = "report.generate";

@Bean public DirectExchange reportExchange() { return new DirectExchange(REPORT_EXCHANGE, true, false); }
@Bean public Queue reportQueue()              { return new Queue(REPORT_QUEUE, true); }
@Bean public Binding reportBinding(...)       { return BindingBuilder.bind(reportQueue).to(reportExchange).with(REPORT_ROUTING_KEY); }
@Bean public Jackson2JsonMessageConverter jackson2JsonMessageConverter() { return new Jackson2JsonMessageConverter(); }
@Bean public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter c) {
    RabbitTemplate t = new RabbitTemplate(cf);
    t.setMessageConverter(c);
    return t;
}
```

```yaml
# application.yml
spring:
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:guest}
    password: ${SPRING_RABBITMQ_PASSWORD:guest}
  # 注：未启用 listener.simple.retry。
  # 业务异常由 ReportJobWorker 顶层 try-catch 路由至 SSE error 事件并恢复 session。
  # listener retry 配置在当前实现下是“伪配置”，会与业务捕获语义割裂，已显式移除。
```

#### B. 消息体

来源文件：`backend/src/main/java/com/interview/messaging/ReportJobMessage.java`

```java
public record ReportJobMessage(Long sessionId, Long userId, String jobId) {}
```

#### C. 生产者

来源文件：`backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`

```java
@Override
public InterviewFinishResponse finish(Long sessionId) {
    Long userId = currentUserId();
    InterviewSession session = requireOngoingSession(sessionId, userId);

    session.setStatus("generating");
    interviewSessionMapper.updateById(session);

    String jobId = java.util.UUID.randomUUID().toString();
    try {
        ReportJobMessage job = new ReportJobMessage(sessionId, userId, jobId);
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.REPORT_EXCHANGE,
            RabbitMqConfig.REPORT_ROUTING_KEY,
            job
        );
        log.info("Published report generation job to RabbitMQ for session {} with jobId {}", sessionId, jobId);
    } catch (Exception e) {
        log.error("Failed to publish report generation job to RabbitMQ for session {}", sessionId, e);
        try {
            InterviewSession restoreSession = interviewSessionMapper.selectById(sessionId);
            if (restoreSession != null && "generating".equals(restoreSession.getStatus())) {
                restoreSession.setStatus("ongoing");
                interviewSessionMapper.updateById(restoreSession);
                log.info("Restored session {} status to ongoing after publish failure", sessionId);
            }
        } catch (Exception restoreEx) {
            log.error("Failed to restore session {} status to ongoing", sessionId, restoreEx);
        }
        throw BusinessException.badRequest("报告生成任务发布失败");
    }

    SESSION_LOCKS.invalidate(sessionId.toString());
    return new InterviewFinishResponse(session.getId(), null, "generating", jobId);
}
```

#### D. 消费者（含幂等保护）

来源文件：`backend/src/main/java/com/interview/service/impl/ReportJobWorker.java`

```java
private static final String STATUS_GENERATING = "generating";
private static final String STATUS_ONGOING   = "ongoing";
private static final String STATUS_FINISHED  = "finished";

@RabbitListener(queues = RabbitMqConfig.REPORT_QUEUE)
public void handleReportJob(ReportJobMessage job) {
    log.info("Received RabbitMQ report job: {}", job);
    processJob(job);
}

private void processJob(ReportJobMessage job) {
    Long sessionId = job.sessionId();
    Long userId = job.userId();
    try {
        UserContext.setCurrentUserId(userId);
        UserContext.setCurrentSessionId(sessionId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("Session {} not found, skipping", sessionId);
            return;
        }
        if (!STATUS_GENERATING.equals(session.getStatus())) {
            // 幂等保护：重复 / 过期消息直接跳过
            log.info("Session {} status is '{}', expected '{}' — skipping duplicate or stale job",
                sessionId, session.getStatus(), STATUS_GENERATING);
            return;
        }
        // ... 报告生成与 SSE 推送 report_ready ...
    } catch (Exception e) {
        // ... 失败路径：SSE error + 回滚到 ongoing ...
    } finally {
        UserContext.remove();
    }
}
```

### 14.3 自动化测试

来源文件：
- `backend/src/test/java/com/interview/service/impl/InterviewServiceImplFinishTest.java`（4 用例）
- `backend/src/test/java/com/interview/service/impl/ReportJobWorkerTest.java`（4 用例）

执行结果：
- `mvn -q test` → `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

### 14.4 Docker Compose BYOK 真实 API Key 功能链路补充
- 2026-06-14 在 Docker Compose 容器环境中关闭 Demo 模式（`APP_DEMO_ENABLED=false`）。
- 通过用户级 OpenAI-compatible BYOK 输入 endpoint 与 API Key，调用模型发现接口后选择运行模型并保存；具体模型仅为运行参数，不作为仓库默认配置或论文模型推荐依据。
- 功能链路结果：
  - 模型发现返回可用模型列表，用户级配置保存后 `hasApiKey=true`；
  - `/api/user/llm-config/test` 返回 `ok=true`；
  - `/finish` 返回 `status=generating` 与 `jobId`；
  - 后端日志出现 `Published report generation job to RabbitMQ`；
  - 后端日志出现 `Received RabbitMQ report job`；
  - 后端日志出现 `Broadcasting event 'report_ready' to 1 SSE emitters`；
  - `interview_session.status=finished`，`summary_report` 长度为 4731；
  - RabbitMQ 队列最终 `messages=0`，`messages_ready=0`，`messages_unacknowledged=0`，`consumers=1`。

### 14.5 严格限制（必须保留）
- 本次验证为**本地 Docker Compose 基础链路联调**：服务可启动、容器可连通、`/finish` → RabbitMQ → `@RabbitListener` → SSE `report_ready` 一次完整链路在 demo 模式（`APP_DEMO_ENABLED=true`）下成功跑通。
- 本次补充验证只证明关闭 Demo 模式后，当前网络与运行模型条件下的一次真实 BYOK 功能链路可用。
- 上述验证**不代表公网高并发压测**；不证明生产级可靠投递；不证明消息绝不丢失；不构成模型推荐依据。
- 当前实现未引入 DLQ、未引入 outbox、未启用 publisher confirm、未做消费并发调优。
- 仍依赖 Redis 承担限流、缓存与状态辅助职责。
