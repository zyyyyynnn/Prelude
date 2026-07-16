# 结束面试与异步报告任务证据（2026-07-13）

## 证据状态

- 基线：`main` @ `851fa5bf12c2f3737c30f13632b7e1759932eacd`
- 关联：`modular-monolith-boundary-hardening-2026-07-13.md`、`quality-gates-2026-07-13.md`
- 状态：已采集，待用户与审查官复核
- 替代关系：正文与答辩引用结束面试 / 报告任务发布时，以本文件为准；旧证据已归档至 `archive/rabbitmq-report-queue-2026-06-13.md`

## 当前链路

```text
/finish
→ FinishInterview
→ generating
→ JobSchedulerPort.enqueue
→ RabbitJobScheduler + RabbitMQ
→ ReportJobWorker
→ ReportGenerateHandler
→ summary_report / finished / report_ready
```

## 源码证据

`InterviewController` 将 `/finish` 委派给应用用例：

```java
@PostMapping("/{sessionId}/finish")
public Result<InterviewFinishResponse> finish(@PathVariable Long sessionId) {
    return Result.success(InterviewApiMapper.toResponse(finishInterview.execute(sessionId)));
}
```

`FinishInterview` 校验归属与状态后置 `generating`，再经作业端口入队：

```java
session.setStatus(STATUS_GENERATING);
interviewSessionRepository.update(session);

JobTicket job;
try {
    job = jobSchedulerPort.enqueue(JobRequest.report(sessionId, userId));
} catch (Exception exception) {
    restoreOngoingStatus(sessionId);
    throw BusinessException.badRequest("报告生成任务发布失败");
}

return new FinishInterviewResult(session.getId(), null, STATUS_GENERATING, job.jobId());
```

`JobSchedulerPort` 与 `RabbitJobScheduler` 将 AMQP 发布收口到基础设施：

```java
public interface JobSchedulerPort {
    JobTicket enqueue(JobRequest request);
}
```

```java
rabbitTemplate.convertAndSend(
    RabbitMqConfig.REPORT_EXCHANGE,
    RabbitMqConfig.REPORT_ROUTING_KEY,
    new ReportJobMessage(request.subjectId(), request.userId(), job.getJobId())
);
```

`ReportJobWorker` 只负责监听并转交应用处理：

```java
@RabbitListener(queues = RabbitMqConfig.REPORT_QUEUE)
public void handleReportJob(ReportJobMessage job) {
    reportGenerateHandler.handle(job);
}
```

## 证据边界

- 应用层通过 `JobSchedulerPort` 发布任务，不再直接依赖 `RabbitTemplate` 或已删除的 `InterviewServiceImpl`。
- `ReportGenerateHandler` 仍直接依赖 `JobExecutionStore`，属于过渡依赖，不得写成边界已完全收敛。
- 本证据证明当前源码组织与本地/CI 可重复路径，不证明 DLQ、outbox、publisher confirm、零丢失或生产可靠投递。

## 可追溯验证

- 相关单元测试：`FinishInterviewTest`、`RabbitJobSchedulerTest`、`ReportJobWorkerTest`、`ReportGenerateHandlerTest`。
- 合并基线质量快照见 `quality-gates-2026-07-13.md`。
