# 模块化单体与面试应用边界证据（2026-07-13）

## 证据状态

- 基线：`main` @ `851fa5bf12c2f3737c30f13632b7e1759932eacd`
- 对应合并：PR #19 `df41b5e21415160691eaf56927d3c5cd5e012c05`；PR #20 `851fa5bf12c2f3737c30f13632b7e1759932eacd`
- 状态：已采集，待用户与审查官复核；未据此修改论文正文

## 两轮变更对应事实

| 轮次 | 已合并事实 | 可支持的论文表述 |
| --- | --- | --- |
| PR #19 | 后端按 `identity`、`resume`、`interview`、`insight`、`catalog` 与 `platform` 收敛为模块化单体；核心模块形成 API、application、domain、infrastructure 分层；前端页面与交互逻辑迁入 feature 目录 | 系统采用单进程部署的模块化单体，业务模块按职责分区，核心链路引入应用用例与端口以逐步隔离基础设施细节 |
| PR #20 | 面试 HTTP DTO 映射集中到 API 适配层；面试 application 使用 Command、Result 与 View 类型；架构测试禁止 application 导入本模块 API 包 | HTTP 请求/响应模型由 API 适配层转换，面试应用层不再依赖 `com.interview.interview.api` DTO |

## 当前源码证据

API 适配层在 `backend/src/main/java/com/interview/interview/api/InterviewApiMapper.java` 完成 HTTP Request 到应用 Command 的转换：

```java
static StartInterviewCommand toCommand(InterviewStartRequest request) {
    return new StartInterviewCommand(
        request.getResumeId(),
        request.getPositionId(),
        request.getJdText(),
        request.getLlmModel()
    );
}
```

`backend/src/main/java/com/interview/interview/api/InterviewController.java` 只向应用用例委派，并在 API 边界调用映射器：

```java
return Result.success(InterviewApiMapper.toResponse(
    startInterview.execute(InterviewApiMapper.toCommand(request))
));
```

`backend/src/test/java/com/interview/architecture/ArchitectureBoundaryTest.java` 固化面试应用层的反向依赖约束：

```java
@Test
void interviewApplicationDoesNotImportItsApiAdapter() throws IOException {
    String sources = readJavaTree(
        backendSourceRoot().resolve(Path.of("com", "interview", "interview", "application"))
    );

    assertThat(sources).doesNotContain("com.interview.interview.api");
}
```

## 证据边界

- 该证据证明当前源码组织、依赖方向和自动化约束，不证明各模块可独立部署；系统仍是模块化单体。
- 该证据不等同于所有应用代码均与 Spring 解耦；SSE 适配仍存在框架类型，不能扩写为完全框架无关。
- `insight.application.ReportGenerateHandler` 当前仍直接依赖 `platform.job.infrastructure.JobExecutionStore`，属于待继续收敛的过渡依赖。
- 该证据不证明所有跨模块依赖已完成最终收敛，也不提供并发、吞吐量或生产可用性结论。
- PR #20 未改变既有 HTTP 路径与前端调用契约；此结论以控制器映射测试与合并 CI 通过为支撑，不扩写为全量兼容性证明。

## 可追溯验证

- PR #19 两次 CI `build` 均通过：Actions runs `29237681005`、`29237710648`。
- PR #20 CI `build` 通过：Actions run `29240597462`。
- PR #20 合并前本地 `mvn --file backend/pom.xml clean test`：222 个测试通过，`jacoco:check` 通过。
- 当前 `backend/pom.xml` 对 `interview.application`、`resume.application`、`insight.application` 设置 instruction coverage `0.70` 门禁。
