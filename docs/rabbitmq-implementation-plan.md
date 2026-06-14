# RabbitMQ 报告任务队列实现计划

> **状态**：已执行并由后续证据取代。当前实现与论文口径以
> `thesis-assets/evidence/code-snippets/impl-2026-06-13-rabbitmq.md`、
> `thesis-assets/meta/final-evidence-lock.md` 和
> `thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md` 为准。
> 本文件保留为历史实施计划，不再作为当前状态依据。
>
> **目标**：引入 RabbitMQ 替换 Redis List 报告任务队列，使 Redis 回归限流、缓存和状态辅助职责。

---

## 1. 背景与现状

### 1.1 历史基线技术栈

- 后端：Spring Boot 3.2.12
- 已有依赖：Redis、WebSocket、Resilience4j、Actuator、Prometheus
- **未引入**：`spring-boot-starter-amqp`
- **未配置**：`docker-compose.yml` 中无 RabbitMQ 服务

### 1.2 历史报告生成链路

```
用户点击"结束面试"
  → InterviewServiceImpl.finish(...)
    → session.status = "generating"
    → Redis List leftPush("queue:report:jobs", ...)
    → 返回 { status: "generating", jobId }
  → ReportJobWorker (CommandLineRunner)
    → 单线程循环 rightPop("queue:report:jobs")
    → 解析 JSON → processJob(...)
    → 报告生成 → SSE broadcast("report_ready")
```

### 1.3 核心变更

> 用 RabbitMQ 替换当前 Redis List 报告任务队列。Redis 保留，但不再承担报告生成任务队列职责。

---

## 2. 完成标准

| # | 标准 |
|---|------|
| 1 | `docker-compose.yml` 可启动 RabbitMQ |
| 2 | 后端可连接 RabbitMQ |
| 3 | `InterviewServiceImpl.finish(...)` 不再写入 Redis List |
| 4 | `InterviewServiceImpl.finish(...)` 改为向 RabbitMQ 发布报告生成任务 |
| 5 | `ReportJobWorker` 不再轮询 Redis List |
| 6 | `ReportJobWorker` 改为通过 `@RabbitListener` 消费 RabbitMQ 队列 |
| 7 | 报告生成完成后的既有业务行为保持不变（session 状态更新、summaryReport 写入、stage 关闭、score_history 写入、user_weakness 写入、SSE 广播 report_ready） |
| 8 | 报告生成失败时：SSE 广播 error；如 session 状态仍为 generating，恢复为 ongoing |
| 9 | Redis 依赖和 Redis 限流逻辑不得删除 |
| 10 | Maven 测试通过 |
| 11 | Docker Compose 配置检查通过 |

---

## 3. 禁止事项

- 删除 Redis 或 Redis 限流逻辑
- 把 RabbitMQ 写成缓存
- 引入 Kubernetes、复杂微服务拆分、死信队列、延迟队列、事务消息等额外能力
- 在没有真实压测数据的情况下写"高并发削峰压测通过"
- 改写与 RabbitMQ 无关的业务逻辑
- 重构章节正文、生成 DOCX/PDF、修改参考文献编号
- 把 RabbitMQ 写成"消息绝不丢失"或"生产级可靠投递已完成"

---

## 4. 执行前检查

```powershell
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$paths = @(
  'backend/pom.xml',
  'backend/src/main/resources/application.yml',
  'docker-compose.yml',
  'backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java',
  'backend/src/main/java/com/interview/service/impl/ReportJobWorker.java',
  'backend/src/main/java/com/interview/config/SseEmitterRegistry.java',
  'thesis-assets/evidence',
  'thesis-assets/defense',
  'thesis-assets/meta'
)

foreach ($p in $paths) {
  if (-not (Test-Path -LiteralPath $p)) {
    throw "Missing required path: $p"
  }
}
```

---

## 5. 后端实现步骤

### 5.1 修改 `backend/pom.xml`

在 `<dependencies>` 中增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**要求**：
- 不删除 `spring-boot-starter-data-redis`、`spring-boot-starter-websocket`、Resilience4j、Actuator / Prometheus
- 不新增与 RabbitMQ 无关的依赖

### 5.2 修改 `docker-compose.yml`

新增 RabbitMQ 服务：

```yaml
rabbitmq:
  image: rabbitmq:3-management
  container_name: prelude-rabbitmq
  restart: always
  ports:
    - "5672:5672"
    - "15672:15672"
  environment:
    RABBITMQ_DEFAULT_USER: guest
    RABBITMQ_DEFAULT_PASS: guest
  volumes:
    - rabbitmq-data:/var/lib/rabbitmq
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
    interval: 10s
    timeout: 5s
    retries: 10
```

修改 `backend.depends_on`：

```yaml
depends_on:
  mysql:
    condition: service_healthy
  redis:
    condition: service_started
  rabbitmq:
    condition: service_healthy
```

在 `backend.environment` 中新增：

```yaml
SPRING_RABBITMQ_HOST: rabbitmq
SPRING_RABBITMQ_PORT: 5672
SPRING_RABBITMQ_USERNAME: guest
SPRING_RABBITMQ_PASSWORD: guest
```

在 `volumes` 中新增：`rabbitmq-data:`

### 5.3 修改 `backend/src/main/resources/application.yml`

在 `spring:` 下新增 RabbitMQ 配置：

```yaml
rabbitmq:
  host: ${SPRING_RABBITMQ_HOST:localhost}
  port: ${SPRING_RABBITMQ_PORT:5672}
  username: ${SPRING_RABBITMQ_USERNAME:guest}
  password: ${SPRING_RABBITMQ_PASSWORD:guest}
  listener:
    simple:
      acknowledge-mode: auto
      retry:
        enabled: true
        initial-interval: 1000ms
        max-attempts: 3
        multiplier: 2.0
```

### 5.4 新增 RabbitMQ 配置类

**文件**：`backend/src/main/java/com/interview/config/RabbitMqConfig.java`

```java
package com.interview.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String REPORT_EXCHANGE = "prelude.report.exchange";
    public static final String REPORT_QUEUE = "prelude.report.generate.queue";
    public static final String REPORT_ROUTING_KEY = "report.generate";

    @Bean
    public DirectExchange reportExchange() {
        return new DirectExchange(REPORT_EXCHANGE, true, false);
    }

    @Bean
    public Queue reportQueue() {
        return new Queue(REPORT_QUEUE, true);
    }

    @Bean
    public Binding reportBinding(Queue reportQueue, DirectExchange reportExchange) {
        return BindingBuilder.bind(reportQueue)
            .to(reportExchange)
            .with(REPORT_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
```

### 5.5 新增消息 DTO

**文件**：`backend/src/main/java/com/interview/messaging/ReportJobMessage.java`

```java
package com.interview.messaging;

public record ReportJobMessage(Long sessionId, Long userId, String jobId) {
}
```

### 5.6 修改 `InterviewServiceImpl`

**文件**：`backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`

**改动**：
- 注入 `RabbitTemplate`
- 引入 `RabbitMqConfig` 和 `ReportJobMessage`
- 删除 `finish(...)` 中 Redis List 入队逻辑
- 改为：

```java
ReportJobMessage job = new ReportJobMessage(sessionId, userId, jobId);
rabbitTemplate.convertAndSend(
    RabbitMqConfig.REPORT_EXCHANGE,
    RabbitMqConfig.REPORT_ROUTING_KEY,
    job
);
log.info("Published report generation job to RabbitMQ for session {} with jobId {}", sessionId, jobId);
```

**异常处理**：RabbitMQ 发布失败时，将 session 状态恢复为 `ongoing`，更新数据库，抛出 `BusinessException.badRequest("报告生成任务发布失败")`。

### 5.7 修改 `ReportJobWorker`

**文件**：`backend/src/main/java/com/interview/service/impl/ReportJobWorker.java`

**改动**：
- 删除 `implements CommandLineRunner`、`StringRedisTemplate`、`ExecutorService`、`run(...)`、`consumeJobs()`
- 删除内部 `ReportJob` record
- 新增：

```java
@RabbitListener(queues = RabbitMqConfig.REPORT_QUEUE)
public void handleReportJob(ReportJobMessage job) {
    log.info("Received RabbitMQ report job: {}", job);
    processJob(job);
}
```

- 将 `processJob(ReportJob job)` 改为 `processJob(ReportJobMessage job)`
- 保留原有报告生成业务逻辑不变

---

## 6. 测试要求

### 6.1 生产者测试

针对 `InterviewServiceImpl.finish(...)`：
- mock `RabbitTemplate`
- 验证调用 `convertAndSend(REPORT_EXCHANGE, REPORT_ROUTING_KEY, ReportJobMessage)`
- 验证返回 `status: "generating"`、`jobId` 非空
- 验证 RabbitMQ 发布失败时 session 状态恢复为 `ongoing` 并抛出业务异常

### 6.2 消费者测试

针对 `ReportJobWorker.handleReportJob(...)`：
- 构造 `ReportJobMessage(sessionId, userId, jobId)`
- mock session、messages、parser、mapper、sseEmitterRegistry
- 成功时验证：session status 更新为 finished、summaryReport 写入、broadcast("report_ready")
- 失败时验证：broadcast("error")、session 状态恢复为 ongoing

---

## 7. 验证命令

```powershell
# Maven 测试
Push-Location backend
mvn -q test
Pop-Location

# Docker Compose 配置检查
docker compose config

# RabbitMQ 服务启动
docker compose up -d rabbitmq

# 端口检查
Get-NetTCPConnection -State Listen |
    Where-Object { $_.LocalPort -in 5672,15672 } |
    Sort-Object LocalPort

# 全量启动
docker compose up -d mysql redis rabbitmq
```

---

## 8. 论文资产更新

代码和测试通过后，更新论文资产（详见 `thesis-assets/meta/workflow-governance.md` 项目漂移闸门规则）。

**统一口径**：

> 系统引入 RabbitMQ 作为报告生成异步任务队列。用户点击结束面试后，/finish 接口负责将会话状态更新为 generating 并发布报告生成任务；后台消费者异步完成报告生成、评分入库与薄弱点提取，完成后通过 SSE 向前端推送 report_ready 事件。Redis 保留用于高频限流、缓存和状态辅助。该设计实现了报告生成链路与用户请求链路的异步解耦，但当前不等同于已完成大规模高并发削峰压测。

**禁止表述**：
- RabbitMQ 已验证高并发削峰能力
- RabbitMQ 支撑万级并发
- RabbitMQ 保证消息不丢失
- RabbitMQ 实现生产级可靠投递
- RabbitMQ 已完成死信队列与重试闭环
- Redis MQ / Redis 消息队列用于报告生成
- RabbitMQ 高并发压测通过

**允许表述**：
- RabbitMQ 报告生成队列
- RabbitMQ 异步任务解耦
- RabbitMQ 将报告生成从 /finish 同步链路中剥离
- RabbitMQ 本地联调通过
- Redis 保留为限流与缓存组件

---

## 9. 验收清单

| # | 验收项 | 状态 |
|---|--------|------|
| 1 | `mvn -q test` 通过 | 已由 `final-evidence-lock.md` 记录 22/22 |
| 2 | `docker compose config` 通过 | 已由 `final-evidence-lock.md` 记录 |
| 3 | RabbitMQ 5672/15672 端口监听正常 | 已由 `impl-2026-06-13-rabbitmq.md` 记录本地 Docker Compose 基础链路 |
| 4 | 后端代码不再使用 Redis List 作为报告任务队列 | 已完成 |
| 5 | `ReportJobWorker` 使用 `@RabbitListener` | 已完成 |
| 6 | `InterviewServiceImpl.finish(...)` 使用 RabbitMQ 发布任务 | 已完成 |
| 7 | 论文资产口径统一为"已引入报告任务队列" | 已由后续证据锁定，正文尚未同步 |
| 8 | 无"已通过高并发压测"的无证据表述 | 作为限制口径保留 |
