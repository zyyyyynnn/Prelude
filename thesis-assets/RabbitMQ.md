# RabbitMQ 报告任务队列补齐开发 Prompt

## 0. 执行环境

你在 Windows 11 + PowerShell 7+ + UTF-8 环境下工作。目标仓库是当前本地 Prelude 项目。

本轮目标是**真正补齐 RabbitMQ 功能**，不是只改文字口径。

---

## 1. 背景与当前项目事实

当前项目中：

- 后端使用 Spring Boot 3.2.12。
- 当前已有 Redis、WebSocket、Resilience4j、Actuator、Prometheus 等依赖。
- 当前 `backend/pom.xml` 尚未引入 `spring-boot-starter-amqp`。
- 当前 `docker-compose.yml` 尚未定义 RabbitMQ 服务。
- 当前报告生成任务队列由 Redis List 实现：
  - `InterviewServiceImpl.finish(...)` 将任务写入 `queue:report:jobs`。
  - `ReportJobWorker` 通过 Redis `rightPop("queue:report:jobs")` 轮询消费任务。
- 报告生成完成后，系统通过 `SseEmitterRegistry.broadcast(sessionId, "report_ready", report)` 通知前端。
- Redis 仍需保留，用于限流、缓存和状态辅助。

本轮要做的核心变更：

> 用 RabbitMQ 替换当前 Redis List 报告任务队列。Redis 保留，但不再承担报告生成任务队列职责。

---

## 2. 完成标准

完成后必须满足：

1. `docker-compose.yml` 可启动 RabbitMQ。
2. 后端可连接 RabbitMQ。
3. `InterviewServiceImpl.finish(...)` 不再写入 Redis List。
4. `InterviewServiceImpl.finish(...)` 改为向 RabbitMQ 发布报告生成任务。
5. `ReportJobWorker` 不再轮询 Redis List。
6. `ReportJobWorker` 改为通过 `@RabbitListener` 消费 RabbitMQ 队列。
7. 报告生成完成后的既有业务行为保持不变：
   - 更新 session 状态为 `finished`
   - 写入 `summaryReport`
   - 关闭当前 stage
   - 写入 `score_history`
   - 写入 `user_weakness`
   - SSE 广播 `report_ready`
8. 报告生成失败时：
   - SSE 广播 `error`
   - 如 session 状态仍为 `generating`，恢复为 `ongoing`
9. Redis 依赖和 Redis 限流逻辑不得删除。
10. Maven 测试通过。
11. Docker Compose 配置检查通过。
12. 论文资产口径更新为“RabbitMQ 已作为报告生成异步任务队列引入”，但不得写成“已通过高并发削峰压测”。

---

## 3. 禁止事项

禁止：

- 删除 Redis。
- 删除 Redis 限流逻辑。
- 把 RabbitMQ 写成缓存。
- 引入 Kubernetes、复杂微服务拆分、死信队列、延迟队列、事务消息等额外能力。
- 在没有真实压测数据的情况下写“高并发削峰压测通过”。
- 改写与 RabbitMQ 无关的业务逻辑。
- 重构章节正文。
- 生成 DOCX/PDF。
- 修改参考文献编号。
- 把 RabbitMQ 写成“消息绝不丢失”或“生产级可靠投递已完成”。

---

## 4. 执行前检查

使用 PowerShell：

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
5. 修改 backend/pom.xml

在 <dependencies> 中增加：

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

要求：

不删除 spring-boot-starter-data-redis。
不删除 spring-boot-starter-websocket。
不删除 Resilience4j。
不删除 Actuator / Prometheus。
不新增与 RabbitMQ 无关的依赖。
6. 修改 docker-compose.yml

新增 RabbitMQ 服务：

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

修改 backend.depends_on：

depends_on:
  mysql:
    condition: service_healthy
  redis:
    condition: service_started
  rabbitmq:
    condition: service_healthy

在 backend.environment 中新增：

SPRING_RABBITMQ_HOST: rabbitmq
SPRING_RABBITMQ_PORT: 5672
SPRING_RABBITMQ_USERNAME: guest
SPRING_RABBITMQ_PASSWORD: guest

在 volumes 中新增：

rabbitmq-data:

要求：

不删除 MySQL。
不删除 Redis。
不删除 Prometheus / Grafana。
不修改已有端口，除非冲突；如冲突必须说明。
7. 修改 backend/src/main/resources/application.yml

在 spring: 下新增 RabbitMQ 配置：

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

要求：

保留 spring.data.redis。
保留 spring.config.import: optional:classpath:application-local.yml。
不复制大量 profile 配置。
8. 新增 RabbitMQ 配置类

新增文件：

backend/src/main/java/com/interview/config/RabbitMqConfig.java

内容要求：

包名：com.interview.config
定义常量：
REPORT_EXCHANGE = "prelude.report.exchange"
REPORT_QUEUE = "prelude.report.generate.queue"
REPORT_ROUTING_KEY = "report.generate"
使用 durable queue。
使用 DirectExchange。
使用 Binding 绑定 queue、exchange、routing key。
定义 Jackson2JsonMessageConverter。
定义使用该 converter 的 RabbitTemplate。

参考实现：

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
9. 新增消息 DTO

新增文件：

backend/src/main/java/com/interview/messaging/ReportJobMessage.java

内容：

package com.interview.messaging;

public record ReportJobMessage(Long sessionId, Long userId, String jobId) {
}

要求：

不继续使用 ReportJobWorker.ReportJob 作为跨组件消息类型。
InterviewServiceImpl 和 ReportJobWorker 都使用该独立 record。
不引入 Lombok。
10. 修改 InterviewServiceImpl

文件：

backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java
10.1 当前逻辑

当前 finish(...) 大致逻辑：

获取当前用户
校验 session
设置 session status 为 generating
生成 jobId
通过 Redis List leftPush("queue:report:jobs", ...) 入队
返回 InterviewFinishResponse(session.getId(), null, "generating", jobId)
10.2 修改目标

改为 RabbitMQ 发布任务。

要求：

注入 RabbitTemplate。
引入：
com.interview.config.RabbitMqConfig
com.interview.messaging.ReportJobMessage
删除 finish(...) 中 Redis List 入队逻辑。
改为：
ReportJobMessage job = new ReportJobMessage(sessionId, userId, jobId);
rabbitTemplate.convertAndSend(
    RabbitMqConfig.REPORT_EXCHANGE,
    RabbitMqConfig.REPORT_ROUTING_KEY,
    job
);
日志改为：
log.info("Published report generation job to RabbitMQ for session {} with jobId {}", sessionId, jobId);
异常处理要求：

如果 RabbitMQ 发布失败：

将 session 状态恢复为 ongoing
更新数据库
抛出 BusinessException.badRequest("报告生成任务发布失败")
保留：
SESSION_LOCKS.invalidate(sessionId.toString());
保留返回：
return new InterviewFinishResponse(session.getId(), null, "generating", jobId);
如果 StringRedisTemplate 在 InterviewServiceImpl 中只用于报告任务队列，则删除该字段和相关 import。
不修改聊天 SSE 逻辑。
11. 修改 ReportJobWorker

文件：

backend/src/main/java/com/interview/service/impl/ReportJobWorker.java
11.1 当前逻辑

当前类：

implements CommandLineRunner
注入 StringRedisTemplate
创建单线程 ExecutorService
启动后循环 rightPop("queue:report:jobs")
解析 JSON 为内部 ReportJob
调用 processJob(...)
11.2 修改目标

改成 RabbitMQ 消费者。

要求：

删除 implements CommandLineRunner。
删除 StringRedisTemplate 字段。
删除 ExecutorService 字段。
删除 run(...)。
删除 consumeJobs()。
删除内部：
public record ReportJob(Long sessionId, Long userId, String jobId) {}
引入：
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.interview.config.RabbitMqConfig;
import com.interview.messaging.ReportJobMessage;
新增监听方法：
@RabbitListener(queues = RabbitMqConfig.REPORT_QUEUE)
public void handleReportJob(ReportJobMessage job) {
    log.info("Received RabbitMQ report job: {}", job);
    processJob(job);
}
将：
private void processJob(ReportJob job)

改为：

private void processJob(ReportJobMessage job)
保留原报告生成业务逻辑，不重写：
UserContext.setCurrentUserId(userId)
UserContext.setCurrentSessionId(sessionId)
查询 session
查询 messages
buildFinishPrompt(...)
Demo 模式报告生成
真实 LLM 调用
interviewReportParser.parse(...)
session status 改为 finished
summaryReport 写入
closeCurrentStage(sessionId)
persistScoreHistory(session, parsedReport)
persistWeaknesses(session, report)
sseEmitterRegistry.broadcast(sessionId, "report_ready", report)
异常时 sseEmitterRegistry.broadcast(sessionId, "error", ...)
异常时恢复 generating 状态为 ongoing
finally UserContext.remove()
12. 测试要求

优先做最小单元测试，不引入 Testcontainers。

至少覆盖：

12.1 生产者测试

针对 InterviewServiceImpl.finish(...)：

mock RabbitTemplate
调用 finish(...)
验证调用：
convertAndSend(
    RabbitMqConfig.REPORT_EXCHANGE,
    RabbitMqConfig.REPORT_ROUTING_KEY,
    ReportJobMessage
)
验证返回 status 为 generating
验证返回 jobId 非空
验证 RabbitMQ 发布失败时：
session 状态恢复为 ongoing
抛出业务异常
12.2 消费者测试

针对 ReportJobWorker.handleReportJob(...)：

构造 new ReportJobMessage(sessionId, userId, jobId)
mock session、messages、parser、mapper、sseEmitterRegistry
成功时验证：
session status 更新为 finished
summaryReport 写入
调用 broadcast(sessionId, "report_ready", report)
失败时验证：
调用 broadcast(sessionId, "error", ...)
如果 session status 为 generating，恢复为 ongoing

如项目当前测试结构不足，至少新增纯单元测试，不要强行上集成测试。

13. 后端验证命令

执行：

Push-Location backend
mvn -q test
Pop-Location
14. Docker 验证命令

执行：

docker compose config
docker compose up -d rabbitmq

验证端口：

Get-NetTCPConnection -State Listen |
    Where-Object { $_.LocalPort -in 5672,15672 } |
    Sort-Object LocalPort

如本地依赖允许，再执行：

docker compose up -d mysql redis rabbitmq

可选：

docker compose ps
15. 论文资产更新

代码和测试通过后，再更新论文资产。

必须修改：

thesis-assets/meta/final-evidence-lock.md
thesis-assets/evidence/figure-table-register.md
thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd
thesis-assets/evidence/diagrams/fig-3.3-system-architecture.svg
thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png
thesis-assets/evidence/code-snippets/impl-2026-06-02.md
thesis-assets/evidence/test-data/env-2026-06.md
thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md
thesis-assets/evidence/review-notes/fig-3.3-redraw-requirements.md
thesis-assets/evidence/review-notes/stage-2-evidence-sync.md
thesis-assets/evidence/figure-assets-plan.md
thesis-assets/defense/package-2026-04-25.md
thesis-assets/defense/script.md
thesis-assets/defense/slide-map.md

视情况最小修改：

thesis-assets/chapters/*.md

限制：

只改 RabbitMQ / MQ / Redis List 报告队列相关句子。
不做全文润色。
不改引用编号。
不生成 DOCX/PDF。
16. 论文统一口径

功能补齐后统一写：

系统引入 RabbitMQ 作为报告生成异步任务队列。用户点击结束面试后，/finish 接口负责将会话状态更新为 generating 并发布报告生成任务；后台消费者异步完成报告生成、评分入库与薄弱点提取，完成后通过 SSE 向前端推送 report_ready 事件。Redis 保留用于高频限流、缓存和状态辅助。该设计实现了报告生成链路与用户请求链路的异步解耦，但当前不等同于已完成大规模高并发削峰压测。
17. 禁止论文表述

不得写：

RabbitMQ 已验证高并发削峰能力
RabbitMQ 支撑万级并发
RabbitMQ 保证消息不丢失
RabbitMQ 实现生产级可靠投递
RabbitMQ 已完成死信队列与重试闭环
Redis MQ
Redis 消息队列用于报告生成
RabbitMQ 高并发压测通过
18. 允许论文表述

可以写：

RabbitMQ 报告生成队列
RabbitMQ 异步任务解耦
RabbitMQ 将报告生成从 /finish 同步链路中剥离
RabbitMQ 本地联调通过
RabbitMQ 管理端口 15672 可访问
Redis 保留为限流与缓存组件
RabbitMQ 的引入改善了任务职责边界
19. 图 3.3 修改要求

文件：

thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd

优先将中间件拆成三个节点：

mysql["MySQL 持久化"]
redis["Redis 限流/缓存"]
rabbitmq["RabbitMQ 报告任务队列"]

如果图过密，可合并为：

MySQL / Redis / RabbitMQ

但必须在图表登记风险说明中写：

RabbitMQ 仅表示报告生成异步任务队列，不代表已完成高并发压测。

重新导出：

thesis-assets/evidence/diagrams/fig-3.3-system-architecture.svg
thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png

如本地无法导出，明确说明原因。

20. final-evidence-lock.md 更新要求

新增或替换 RabbitMQ 口径锁定：

## RabbitMQ 口径锁定

- RabbitMQ 已作为报告生成异步任务队列引入，用于替换原 Redis List 报告任务队列。
- Redis 保留为限流、缓存和状态辅助组件，不再承担报告生成任务队列职责。
- RabbitMQ 当前只证明异步任务解耦机制已实现；若无新增压测证据，不得写成高并发削峰性能已验证。
- 若后续需要声明生产级可靠投递，必须补充确认机制、死信队列、重试策略、幂等处理和故障恢复测试。
21. impl-2026-06-02.md 更新要求

将证据 12 标题统一为：

## 证据 12：RabbitMQ 报告任务队列、Resilience4j 熔断与容器化可观测性

正文写明：

原 Redis List 报告任务队列已替换为 RabbitMQ。
InterviewServiceImpl.finish(...) 发布 ReportJobMessage 到 RabbitMQ。
ReportJobWorker 通过 @RabbitListener 消费队列。
完成后广播 report_ready。
Redis 仍用于限流/缓存。
不得写“高并发压测通过”。
22. env-2026-06.md 更新要求

RabbitMQ 行：

| RabbitMQ | 3-management / Running | 报告生成异步任务队列，管理端口 15672，AMQP 端口 5672 |

Redis 行：

| Redis | 7.0+ / Running | 高频限流、缓存与状态辅助，不再承担报告生成任务队列 |

如果未实际启动 RabbitMQ，不得写 Running，应写：

| RabbitMQ | 待验证 | 已纳入配置，需完成本地启动验证 |
23. defense 三份文件更新要求

修改：

thesis-assets/defense/package-2026-04-25.md
thesis-assets/defense/script.md
thesis-assets/defense/slide-map.md

统一为：

RabbitMQ 已引入，用于报告生成异步任务队列。
Redis 用于限流/缓存。
不再写 Redis List 是当前最终方案。
不写 RabbitMQ 已通过高并发削峰压测。

Q&A 推荐回答：

报告生成属于耗时任务，直接挂在 /finish 同步链路上会影响交互响应。RabbitMQ 将报告生成任务从用户请求中解耦，/finish 只负责改变会话状态并投递任务，后台消费者完成报告生成后通过 SSE 推送 report_ready。Redis 保留在限流和状态辅助场景，职责更清晰。当前验证范围是本地功能链路，不等同于高并发削峰压测。
24. 阶段报告处理原则

thesis-assets/evidence/phase-reports/ 属于历史审查记录，不要大面积改写历史结论。

如需统一口径，只在相关文件顶部追加：

> [2026-06 RabbitMQ Update]
> 后续实现阶段已正式引入 RabbitMQ 报告任务队列，用于替换当时文档中提到的 Redis List 报告任务队列。历史阶段报告保留原始审查语境，不作为最终 RabbitMQ 实现状态的唯一依据。

不要把历史报告伪造成当时已完成 RabbitMQ。

25. chapters 修改规则

如果修改 thesis-assets/chapters/*.md：

只改 RabbitMQ / MQ / Redis List / Redis MQ 相关句子。
不改参考文献编号。
不大段润色。
不改变章节结构。
不引入“万级并发”“生产级可靠投递”“完全不丢消息”等无证据表述。

重点检查：

thesis-assets/chapters/chapter-06-conclusion.md

将：

Redis MQ 消息队列

统一改为：

RabbitMQ 报告任务队列

同时将“暴力削峰填谷”“高并发下已验证”等夸张表述降调为“异步解耦机制”。

26. 全局搜索与修复

执行：

Select-String -Path 'backend/**/*.java','backend/**/*.yml','backend/pom.xml','docker-compose.yml','thesis-assets/**/*.md','thesis-assets/evidence/diagrams/*.mmd' `
  -Pattern 'Redis MQ','Redis List','RabbitMQ','MQ','消息队列','异步队列','削峰','queue:report:jobs' `
  -Encoding UTF8

处理规则：

代码中不应再出现 queue:report:jobs，除非作为历史注释；不建议保留历史注释。
当前论文口径中不应再说“当前未引入 RabbitMQ”。
Redis List 只能出现在“旧方案已替换”的语境。
RabbitMQ 可写成“已引入报告任务队列”。
不得写成“已完成高并发削峰压测”。
27. 最终验证命令

执行：

# 1. Maven 测试
Push-Location backend
mvn -q test
Pop-Location

# 2. Docker Compose 配置检查
docker compose config

# 3. RabbitMQ 服务启动
docker compose up -d rabbitmq

# 4. RabbitMQ 端口检查
Get-NetTCPConnection -State Listen |
    Where-Object { $_.LocalPort -in 5672,15672 } |
    Sort-Object LocalPort

# 5. 残留旧队列检查
Select-String -Path 'backend/**/*.java','backend/**/*.yml','backend/pom.xml','docker-compose.yml','thesis-assets/**/*.md','thesis-assets/evidence/diagrams/*.mmd' `
  -Pattern 'Redis MQ','queue:report:jobs','当前系统未引入 RabbitMQ','不得写成已实现 MQ','未引入 RabbitMQ' `
  -Encoding UTF8

# 6. 高风险夸张口径检查
Select-String -Path 'thesis-assets/**/*.md' `
  -Pattern '万级并发','生产级可靠投递','完全不丢','保证不丢','高并发削峰压测通过','暴力削峰填谷','彻底保证' `
  -Encoding UTF8
28. 验收要求

必须满足：

mvn -q test 通过。
docker compose config 通过。
RabbitMQ 5672、15672 端口监听正常，或明确说明未能验证原因。
后端代码不再使用 Redis List 作为报告任务队列。
ReportJobWorker 使用 @RabbitListener。
InterviewServiceImpl.finish(...) 使用 RabbitMQ 发布任务。
论文资产中 RabbitMQ 口径统一为“已引入报告任务队列”。
Redis 口径统一为“限流 / 缓存 / 状态辅助”。
无“已通过高并发压测”的无证据表述。
29. 交付格式

最后输出：

修改摘要。
后端代码修改清单。
配置与 Docker 修改清单。
测试文件修改 / 新增清单。
论文资产口径修改清单。
RabbitMQ 当前实现边界。
Redis 当前保留职责。
验证命令输出摘要。
未验证项及原因。