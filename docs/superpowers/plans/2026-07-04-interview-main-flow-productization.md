# 沉浸式模拟面试主链路产品化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加 Interview Preflight、后台评分驱动的结构化训练报告和同步升级的 dev fixture，同时保留现有运行链路与旧报告兼容。

**Architecture:** `InterviewReportParser` 解析不含派生分数和 weaknesses 的叙述草稿，`InterviewReportAssembler` 统一从消息、阶段和 `UserWeakness` 合并最终 JSON；`ReportJobWorker` 与 `DevFixtureService` 共用该组件。前端以结构化 JSON 为主、Markdown 为 fallback，并在空工作台非阻塞展示 Preflight。

**Tech Stack:** Java 21、Spring Boot 3.2、MyBatis-Plus、JUnit 5/Mockito、Vue 3、TypeScript、shadcn-vue、Tailwind CSS、Playwright。

---

### Task 1: 结构化报告模型与容错 Parser

**Files:**
- Create: `backend/src/main/java/com/interview/dto/StructuredInterviewReport.java`
- Create: `backend/src/main/java/com/interview/dto/InterviewReportDraft.java`
- Modify: `backend/src/main/java/com/interview/service/impl/InterviewReportParser.java`
- Modify: `backend/src/test/java/com/interview/service/impl/InterviewReportParserTest.java`

- [ ] **Step 1: 写失败测试**

覆盖完整 draft JSON、字段缺失、非法 JSON、越界三维分数，并断言 parser 输出不接受 overall、stage score、question score 或 weaknesses。

```java
@Test
void parsesNarrativeDraftAndClampsDimensionScores() {
    InterviewReportDraft draft = parser.parse("""
        {"summary":{"fitAssessment":"继续投递","actionRecommendation":"补强后复试","overallRisk":"项目量化不足"},
         "scores":{"technical":12,"expression":0,"logic":8},
         "stagePerformances":[{"stageName":"technical","summary":"基础稳定","positiveSignals":["结构清楚"],"negativeSignals":[],"improvementSuggestions":["补充指标"]}],
         "strengths":["结构化表达"],"trainingPlan":{"threeDay":["复盘"],"sevenDay":["专项训练"],"nextInterviewFocus":["量化表达"]},
         "finalAdvice":"继续训练","reportMarkdown":"# 报告"}
        """);
    assertThat(draft.scores().technical()).isEqualTo(10);
    assertThat(draft.scores().expression()).isEqualTo(1);
    assertThat(draft.stagePerformances().getFirst().stageName()).isEqualTo("technical");
}
```

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -Dtest=InterviewReportParserTest test`

Expected: 编译失败，缺少 `InterviewReportDraft` 或新 parser 返回类型。

- [ ] **Step 3: 最小实现**

创建公开 DTO records；parser 去除 JSON fence、钳制 1..10、归一化 null list，并为非法/缺失 JSON 返回含安全文案和 Markdown fallback 的 draft。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `mvn -f backend/pom.xml -Dtest=InterviewReportParserTest test`

Expected: PASS。

### Task 2: 共享结构化报告 Assembler

**Files:**
- Create: `backend/src/main/java/com/interview/service/impl/InterviewReportAssembler.java`
- Create: `backend/src/test/java/com/interview/service/impl/InterviewReportAssemblerTest.java`

- [ ] **Step 1: 写失败测试**

测试 assistant→user 配对、user 行 score/hint 来源、按阶段时间窗口计算均分、总体分为三维均值、缺题/缺分兼容及 weakness 格式化。

```java
@Test
void assemblesScoresOnlyFromPersistedUserMessages() {
    StructuredInterviewReport report = assembler.assemble(
        draft(8, 7, 9), List.of(stage("technical", at(10), at(20))),
        List.of(assistant("问题", at(11)), user("回答", 6, "缺少量化", at(12))),
        List.of(weakness("性能", "缺少指标")));
    assertThat(report.questionReviews().getFirst().score()).isEqualTo(6);
    assertThat(report.questionReviews().getFirst().scoringReason()).isEqualTo("缺少量化");
    assertThat(report.stagePerformances().getFirst().score()).isEqualTo(6.0);
    assertThat(report.scores().overall()).isEqualTo(8.0);
    assertThat(report.weaknesses()).containsExactly("性能：缺少指标");
}
```

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -Dtest=InterviewReportAssemblerTest test`

Expected: 缺少 assembler。

- [ ] **Step 3: 最小实现**

实现纯函数组件。阶段归属以 `startedAt <= createdAt <= endedAt` 判断；无 endedAt 使用后续阶段起点或开放区间。只读取 `role=user` 的 score/hint；叙述性 question improvement 使用对应阶段 draft 建议或安全 fallback。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `mvn -f backend/pom.xml -Dtest=InterviewReportAssemblerTest test`

Expected: PASS。

### Task 3: RabbitMQ 报告链路接入共享合并器

**Files:**
- Modify: `backend/src/main/java/com/interview/service/impl/ReportJobWorker.java`
- Modify: `backend/src/test/java/com/interview/service/impl/ReportJobWorkerTest.java`

- [ ] **Step 1: 改写失败测试**

断言 worker 在薄弱点落库后查询同 session weaknesses，调用 assembler，保存/广播完全相同的结构化 JSON；失败仍恢复 ongoing 并广播 error。

```java
verify(interviewReportAssembler).assemble(eq(draft), eq(stages), eq(messages), eq(persistedWeaknesses));
verify(sseEmitterRegistry).broadcast(7L, "report_ready", structuredJson);
assertThat(session.getSummaryReport()).isEqualTo(structuredJson);
```

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -Dtest=ReportJobWorkerTest test`

Expected: worker 未注入/调用 assembler。

- [ ] **Step 3: 最小实现**

扩展 LLM JSON schema 只含叙述字段；保留 ScoreHistory 与 weakness 提取；合并后通过 ObjectMapper 序列化 final report。保持现有 Rabbit listener、状态恢复和 SSE 名称。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `mvn -f backend/pom.xml -Dtest=ReportJobWorkerTest test`

Expected: PASS。

### Task 4: dev fixture 使用同一报告路径

**Files:**
- Modify: `backend/src/main/java/com/interview/service/DevFixtureCatalog.java`
- Modify: `backend/src/main/java/com/interview/service/DevFixtureService.java`
- Create: `backend/src/main/resources/demo/report-java.json`
- Create: `backend/src/main/resources/demo/report-frontend.json`
- Create: `backend/src/main/resources/demo/report-algorithm.json`
- Keep: `backend/src/main/resources/demo/report-template.md` as the Java markdown fallback text source until the JSON resource contains the same text verbatim
- Create: `backend/src/test/java/com/interview/service/DevFixtureCatalogTest.java`
- Create: `backend/src/test/java/com/interview/service/DevFixtureServiceTest.java`

- [ ] **Step 1: 写失败测试**

验证三个岗位 report 都能被 parser 解析；finished session 的每条 user 消息有 `resolveMockJudge` 生成的 score/hint；消息时间落在 `QnaPair.stageName` 窗口；最终 summaryReport 来自共享 assembler。

```java
verify(interviewReportAssembler, times(3)).assemble(any(), anyList(), anyList(), anyList());
assertThat(insertedUserMessages).allSatisfy(message -> {
    assertThat(message.getScore()).isBetween(7, 9);
    assertThat(message.getHint()).isNotBlank();
});
```

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -Dtest=DevFixtureCatalogTest,DevFixtureServiceTest test`

Expected: fixture 仍是 Markdown，user score/hint 为空。

- [ ] **Step 3: 最小实现**

保留当前报告文本内容，转换成 draft JSON；在每个阶段窗口内按阶段内题号分配时间；解析 `resolveMockJudge` 并写入 user message；查询刚插入的 stages/messages/weaknesses 后调用共享 assembler。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `mvn -f backend/pom.xml -Dtest=DevFixtureCatalogTest,DevFixtureServiceTest test`

Expected: PASS。

### Task 5: Interview Preflight 后端契约

**Files:**
- Create: `backend/src/main/java/com/interview/dto/InterviewPreflightRequest.java`
- Create: `backend/src/main/java/com/interview/dto/InterviewPreflightResponse.java`
- Create: `backend/src/main/java/com/interview/service/impl/InterviewPreflightService.java`
- Modify: `backend/src/main/java/com/interview/service/InterviewService.java`
- Modify: `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
- Modify: `backend/src/main/java/com/interview/controller/InterviewController.java`
- Create: `backend/src/test/java/com/interview/service/impl/InterviewPreflightServiceTest.java`
- Modify: `backend/src/test/java/com/interview/controller/InterviewControllerWebMvcTest.java`

- [ ] **Step 1: 写失败测试**

覆盖本人简历、非本人简历、岗位缺失、JD 为空、rawText 为空、LLM 非 JSON、自然等级白名单、dev fixture 和 Controller validation/service 参数。

```java
mockMvc.perform(post("/api/interview/preflight")
        .header("Authorization", "Bearer token")
        .contentType(APPLICATION_JSON)
        .content("{\"resumeId\":1,\"positionId\":2,\"jdText\":\"JD\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.fitLevel").value("中等偏高"));
```

- [ ] **Step 2: 运行并确认 RED**

Run: `mvn -f backend/pom.xml -Dtest=InterviewPreflightServiceTest,InterviewControllerWebMvcTest test`

Expected: endpoint/service 不存在。

- [ ] **Step 3: 最小实现**

Service 复用 mappers、UserContext、LlmRouter 与 DevFixtureService；LLM prompt 不要求百分比分数。异常时返回保守 fallback，验证错误仍抛业务异常。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `mvn -f backend/pom.xml -Dtest=InterviewPreflightServiceTest,InterviewControllerWebMvcTest test`

Expected: PASS。

### Task 6: 前端 contract 与报告解析

**Files:**
- Modify: `frontend/src/api/contracts.ts`
- Modify: `frontend/src/api/interview.ts`
- Create: `frontend/src/utils/interviewReport.ts`
- Modify: `frontend/tests/_helpers/mock-api.ts`

- [ ] **Step 1: 增加可执行 contract 验证场景**

在 Playwright mock 中返回 preflight 和结构化 summaryReport，并保留纯 Markdown session。先让后续 UI spec 因缺少类型/API/解析器失败。

- [ ] **Step 2: 运行并确认 RED**

Run: `npm --prefix frontend run build`

Expected: 新 mock 类型引用缺失。

- [ ] **Step 3: 最小实现**

新增 `InterviewPreflight*`、`StructuredInterviewReport` TypeScript 类型、`fetchInterviewPreflight` 和 `parseInterviewReport`。解析器仅对外部数据做 runtime guard，失败返回 `{ kind: 'markdown', markdown }`。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `npm --prefix frontend run build`

Expected: PASS。

### Task 7: 删除面试中实时评分 UI 并补 DESIGN

**Files:**
- Modify: `frontend/src/components/workspace/MessageThread.vue`
- Modify: `DESIGN.md`
- Modify: `frontend/tests/visual/ui-visual.spec.ts`

- [ ] **Step 1: 写失败 Playwright 断言**

载入含 score/hint 的消息，断言页面没有“评分：”和 hint 文本。

```ts
await expect(page.getByText(/评分：\d+\/10/)).toHaveCount(0)
await expect(page.getByText('缺少量化依据')).toHaveCount(0)
```

- [ ] **Step 2: 运行并确认 RED**

Run: `npm --prefix frontend run capture:visual -- --grep "interview message"`

Expected: 仍显示评分 pill。

- [ ] **Step 3: 最小实现**

删除 judge 模板、Tooltip import 和全部 judge/fade CSS；DESIGN 6.3 明确后台保存、报告统一展示。

- [ ] **Step 4: 运行并确认 GREEN**

重复 Playwright grep，Expected: PASS。

### Task 8: Preflight 面板

**Files:**
- Create: `frontend/src/components/workspace/InterviewPreflightPanel.vue`
- Modify: `frontend/src/components/workspace/InterviewComposer.vue`
- Modify: `frontend/src/views/InterviewView.vue`
- Modify: `DESIGN.md`
- Modify: `frontend/tests/visual/ui-visual.spec.ts`
- Modify: `frontend/tests/a11y/ui-a11y.spec.ts`

- [ ] **Step 1: 写失败 Playwright 测试**

断言选择数据后出现 loading→自然等级、命中能力、缺口、风险、重点和四阶段计划；模拟 500 时出现重试且开始按钮仍启用；测试 390px 不溢出。

- [ ] **Step 2: 运行并确认 RED**

Run: `npm --prefix frontend run capture:visual -- --grep "preflight"`

Expected: 面板不存在。

- [ ] **Step 3: 最小实现**

在 `InterviewView` 维护 debounce、abort、loading/error/data；Composer 暴露 JD 变化；面板复用 Card/Badge/Button 和语义 token。DESIGN 增加状态与响应式规则。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `npm --prefix frontend run capture:visual -- --grep "preflight"`

Expected: PASS。

### Task 9: 结构化报告组件与 PDF

**Files:**
- Create: `frontend/src/components/report/StructuredReportPanel.vue`
- Create: `frontend/src/components/report/ReportScoreCard.vue`
- Create: `frontend/src/components/report/StagePerformanceList.vue`
- Create: `frontend/src/components/report/QuestionReviewList.vue`
- Create: `frontend/src/components/report/TrainingPlanPanel.vue`
- Modify: `frontend/src/views/InterviewView.vue`
- Modify: `frontend/src/utils/pdf.ts`
- Modify: `DESIGN.md`
- Modify: `frontend/tests/visual/ui-visual.spec.ts`
- Modify: `frontend/tests/a11y/ui-a11y.spec.ts`

- [ ] **Step 1: 写失败 Playwright 测试**

断言结构化报告七部分可见、列表为 `ul/ol`、旧 Markdown 可见、缺字段不白屏、导出触发 `interview-report.pdf` 且非空、小屏无水平溢出。

- [ ] **Step 2: 运行并确认 RED**

Run: `npm --prefix frontend run capture:visual -- --grep "structured report"`

Expected: 仅有 Markdown 页面。

- [ ] **Step 3: 最小实现**

创建职责单一组件并接入 `InterviewView`；根节点保持 `reportRef`；扩展 PDF avoid selector；DESIGN 6.5 固化布局、badge、状态和分页规则。

- [ ] **Step 4: 运行并确认 GREEN**

Run: `npm --prefix frontend run capture:visual -- --grep "structured report"`

Expected: PASS。

### Task 10: 产品文档、全量静态验证与 CodeGraph

**Files:**
- Create: `docs/product/interview-main-flow.md`
- Modify: `docs/quality/ui-quality-system.md`
- Create/update: `frontend/tests/visual/__screenshots__/18-interview-preflight.png`
- Create/update: `frontend/tests/visual/__screenshots__/19-structured-report.png`
- Create/update: `frontend/tests/visual/__screenshots__/20-structured-report-mobile.png`

- [ ] **Step 1: 写产品边界文档**

覆盖定位、主链路、Preflight 字段、阶段、后台评分策略、报告 schema、能力画像、无 MCP 原因、service 边界和 fixture 同步约束。

- [ ] **Step 2: 同步 UI 质量文档与视觉状态**

记录新增场景、artifact-only 策略和报告/PDF覆盖。

- [ ] **Step 3: 运行全量静态与自动化验证**

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -q -DskipTests package
npm --prefix frontend run build
npm --prefix frontend run verify:ui
npm --prefix frontend run verify:tokens
npm --prefix frontend run verify:a11y
npm --prefix frontend run capture:visual
codegraph sync
git diff --check
```

Expected: 全部 PASS；视觉变化仅限新 preflight/report 与实时评分移除。

### Task 11: local/dev 端到端验收

**Files:**
- No source changes unless a test exposes an in-scope defect.

- [ ] **Step 1: 启动 local/dev**

Run: `.\start-dev.bat`

Expected: MySQL/Redis/RabbitMQ 启动，backend 8080、frontend 5173 可访问。

- [ ] **Step 2: 重建 fixture**

登录 `demo / 123456` 获取 JWT，调用：

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/dev-fixtures/reset -Headers @{ Authorization = "Bearer $token" }
```

Expected: 200；三个 finished session 的 summaryReport 为结构化 JSON，user 消息 score/hint 非空。

- [ ] **Step 3: 浏览器验收**

验证历史 fixture 报告、新创建文字报告、旧 Markdown fallback、语音数据层报告兼容、Preflight 失败仍可开始、PDF 下载非空、桌面与 390px 无溢出。

- [ ] **Step 4: 最终完成审计**

逐条对照目标 18 节，记录每个要求对应文件、测试或运行时证据；未验证项不得标记完成。
