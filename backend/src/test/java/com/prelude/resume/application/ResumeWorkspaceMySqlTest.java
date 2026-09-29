package com.prelude.resume.application;

import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeTurn;
import com.prelude.test.ResumeDataFixtures;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The workspace queue against real MySQL: what a submitted turn is allowed to become, who is
 * allowed to run it, and what happens when nobody finishes.
 *
 * <p>These run against the database rather than mocks because every guarantee under test lives
 * in a conditional statement or a unique key. A mocked repository would happily accept an
 * update that reaches no row, which is the exact bug this file exists to keep out.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "prelude.jobs.scheduling-enabled=false"
})
class ResumeWorkspaceMySqlTest {

    @Autowired
    private ResumeWorkspaceService workspace;

    @Autowired
    private ResumeWorkspaceRepository repository;

    @Autowired
    private com.prelude.resume.application.repository.ResumeTranscriptRepository transcript;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("取消置顶把 NULL 真的写进库，而不是报成功却什么都不改")
    void unpinningClearsTheStoredTimestamp() {
        var fixture = ResumeDataFixtures.workspace(jdbc, "unpin");
        workspace.pinConversation(fixture.accountId(), fixture.conversationId(), true);
        assertThat(pinnedAt(fixture.conversationId())).isNotNull();

        workspace.pinConversation(fixture.accountId(), fixture.conversationId(), false);

        assertThat(pinnedAt(fixture.conversationId())).isNull();
        assertThat(workspace.listConversations(fixture.accountId()))
            .singleElement()
            .satisfies(row -> assertThat(row.pinned()).isFalse());
    }

    @Test
    @DisplayName("同一账户里被置顶的会话排在前面")
    void pinnedConversationsComeFirst() {
        var fixture = ResumeDataFixtures.workspace(jdbc, "pin-order");
        Long second = workspace.createConversation(fixture.accountId(), fixture.resumeId()).id();
        workspace.pinConversation(fixture.accountId(), second, true);

        assertThat(workspace.listConversations(fixture.accountId()))
            .extracting(row -> row.id())
            .containsExactly(second, fixture.conversationId());
    }

    @Test
    @DisplayName("一条轮次只会被一个领取者跑一次")
    void onlyOneCallerWinsTheClaim() throws Exception {
        var fixture = ResumeDataFixtures.workspace(jdbc, "claim");
        Long turnId = submit(fixture.accountId(), fixture.conversationId(), "把项目写成结果导向");

        AtomicInteger wins = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> attempts = List.of(
                executor.submit(() -> claim(ready, go, turnId, wins)),
                executor.submit(() -> claim(ready, go, turnId, wins)));
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(wins.get()).isEqualTo(1);
        assertThat(status(turnId)).isEqualTo("running");
    }

    @Test
    @DisplayName("跑不动的轮次落到 failed 并带上原因，不会把会话永久卡在运行中")
    void aFailedRunEndsTheTurn() {
        // An account with nothing to patch: the run refuses before any model call, which is the
        // cheapest real failure this path can produce.
        long accountId = com.prelude.test.AccountFixtures.create(jdbc, "fail-account");
        long conversationId = ResumeDataFixtures.unattachedConversation(jdbc, accountId);
        Long turnId = repository.enqueueTurn(ResumeTurn.queued(conversationId, accountId,
            "把技能写成一行的", List.of(), List.of(), 0, LocalDateTime.now()));

        new ResumeTurnProcessor(repository, failingRunner()).process(
            repository.findTurn(accountId, turnId).orElseThrow());

        // The processor records the outcome instead of rethrowing: the instruction is over and
        // its reason belongs on screen, not in a request error.
        assertThat(status(turnId)).isEqualTo("failed");
        assertThat(failureReason(turnId)).contains("还没有导入可修改的简历");
        assertThat(repository.findStaleRunningTurns(LocalDateTime.now().minusHours(1), 10))
            .extracting(ResumeTurn::id)
            .doesNotContain(turnId);
    }

    @Test
    @DisplayName("清扫只收走失去执行者的轮次，正在跑的不动，且不会重复收口")
    void abandonedTurnsAreSweptAndLiveTurnsAreLeftAlone() {
        var fixture = ResumeDataFixtures.workspace(jdbc, "sweep");
        Long abandoned = submit(fixture.accountId(), fixture.conversationId(), "把概览改短");
        repository.claimTurn(abandoned, LocalDateTime.now().minusHours(2));
        Long inFlight = submit(fixture.accountId(), fixture.conversationId(), "再把技能写成一行的");
        repository.claimTurn(inFlight, LocalDateTime.now());

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        var swept = repository.findStaleRunningTurns(cutoff, 50);

        // The persistent test database carries abandoned rows from earlier runs, so membership
        // in a capped page proves nothing. What the sweep must guarantee is that it only ever
        // offers turns whose start predates the cutoff.
        assertThat(swept).allSatisfy(turn -> assertThat(turn.startedAt()).isBefore(cutoff));
        assertThat(swept).extracting(ResumeTurn::id).doesNotContain(inFlight);

        assertThat(repository.finishTurn(abandoned, ResumeTurn.Status.FAILED, "运行中断，未收到结果",
            LocalDateTime.now())).isTrue();
        assertThat(repository.finishTurn(abandoned, ResumeTurn.Status.DONE, null, LocalDateTime.now()))
            .as("a turn already closed cannot be closed again")
            .isFalse();
        assertThat(status(abandoned)).isEqualTo("failed");
        assertThat(status(inFlight)).isEqualTo("running");
    }

    @Test
    @DisplayName("没开始的轮次可以撤回，已经跑起来的不能")
    void onlyAQueuedTurnCanBeWithdrawn() {
        var fixture = ResumeDataFixtures.workspace(jdbc, "cancel");
        Long queued = submit(fixture.accountId(), fixture.conversationId(), "第一条");
        workspace.cancel(fixture.accountId(), fixture.conversationId(), queued);
        assertThat(status(queued)).isEqualTo("cancelled");

        Long running = submit(fixture.accountId(), fixture.conversationId(), "第二条");
        repository.claimTurn(running, LocalDateTime.now());
        assertThatThrownBy(() ->
            workspace.cancel(fixture.accountId(), fixture.conversationId(), running))
            .hasMessageContaining("已经在执行");
        assertThat(status(running)).isEqualTo("running");
    }

    @Test
    @DisplayName("会话不能挂到别人的简历上，也读不到别人的会话")
    void conversationsStayInsideTheirAccount() {
        var owner = ResumeDataFixtures.workspace(jdbc, "owner");
        var stranger = ResumeDataFixtures.workspace(jdbc, "stranger");

        assertThatThrownBy(() -> workspace.createConversation(stranger.accountId(), owner.resumeId()))
            .hasMessageContaining("简历不存在");
        assertThatThrownBy(() -> workspace.listTurns(stranger.accountId(), owner.conversationId()))
            .hasMessageContaining("会话不存在");
        assertThatThrownBy(() -> workspace.pinConversation(stranger.accountId(), owner.conversationId(), true))
            .hasMessageContaining("会话不存在");
        assertThat(pinnedAt(owner.conversationId())).isNull();
    }

    /**
     * The field names the workspace stream reads, asserted on the serialized response rather
     * than on the Java types: the frontend consumes `toolCalls` with `icon`/`text` steps and an
     * optional measured `files` chip, and a rename on this side is invisible to every backend
     * test while it silently empties the trace in the browser.
     */
    @Test
    @DisplayName("出站 wire 保持工作区所读的形状")
    void theResponseKeepsTheShapeTheWorkspaceReads() {
        var fixture = ResumeDataFixtures.workspace(jdbc, "wire-shape");
        Long turnId = submit(fixture.accountId(), fixture.conversationId(), "把技能写成一行");
        repository.claimTurn(turnId, LocalDateTime.now());

        long snapshotId = ResumeDataFixtures.snapshot(jdbc, fixture.accountId());
        var run = new com.prelude.resume.domain.ResumeAgentRun(null, turnId,
            fixture.conversationId(), fixture.resumeId(), 1, snapshotId,
            com.prelude.llm.api.PromptIds.RESUME_ASSISTANT,
            com.prelude.resume.domain.ResumeAgentRun.Status.PROPOSAL_READY, null,
            LocalDateTime.now(), LocalDateTime.now());
        Long runId = transcript.openRun(run);
        transcript.appendMessage(turnId, fixture.conversationId(), "按量化口径改写技能块。",
            LocalDateTime.now());
        transcript.appendStep(new com.prelude.resume.domain.ResumeAgentStep(
            null, runId, 0, com.prelude.resume.domain.ResumeAgentStep.Kind.THINK,
            "梳理指令与文档结构", null, null, null,
            List.of(), List.of(), List.of("基线版本 r1"), null, null,
            com.prelude.resume.domain.ResumeAgentStep.State.DONE, null));
        transcript.appendStep(new com.prelude.resume.domain.ResumeAgentStep(
            null, runId, 1, com.prelude.resume.domain.ResumeAgentStep.Kind.READ,
            "查看文档结构", "list_blocks", null, "6 块",
            List.of(), List.of("6 块"), List.of("概览-1 · 概览"), null, null,
            com.prelude.resume.domain.ResumeAgentStep.State.DONE, null));
        transcript.appendStep(new com.prelude.resume.domain.ResumeAgentStep(
            null, runId, 2, com.prelude.resume.domain.ResumeAgentStep.Kind.WRITE,
            "改写技能块", null, null, null,
            List.of(), List.of(), List.of("Java（三年生产经验）"), null, null,
            com.prelude.resume.domain.ResumeAgentStep.State.DONE, null));
        transcript.appendStep(new com.prelude.resume.domain.ResumeAgentStep(
            null, runId, 3, com.prelude.resume.domain.ResumeAgentStep.Kind.SEARCH,
            "检索原始材料", "search_material", null, "1 命中",
            List.of(), List.of("P99"), List.of(), null, null,
            com.prelude.resume.domain.ResumeAgentStep.State.DONE, null));
        transcript.appendStep(new com.prelude.resume.domain.ResumeAgentStep(
            null, runId, 4, com.prelude.resume.domain.ResumeAgentStep.Kind.PROPOSAL,
            "生成补丁提案", null, null, null,
            List.of(new com.prelude.resume.domain.ResumeAgentStep.FileDiff("resume.md", 22, 10)),
            List.of(), List.of(), "待确认",
            com.prelude.resume.domain.ResumeAgentStep.BadgeTone.DEFAULT,
            com.prelude.resume.domain.ResumeAgentStep.State.ERROR, "块不存在"));

        String json = new tools.jackson.databind.ObjectMapper()
            .writeValueAsString(workspace.listTurns(fixture.accountId(), fixture.conversationId()));

        assertThat(json)
            .contains("\"toolCalls\"")
            // The header counts the kinds that really ran, in the workspace's own words, and a
            // kind that did not happen is left out rather than padded in.
            .contains("\"summary\":\"思考 1轮 · 读1次文件、改1次文件、查找1次\"")
            .contains("\"icon\":\"read\"")
            .contains("\"text\":\"查看文档结构\"")
            .contains("\"files\":[{\"name\":\"resume.md\",\"add\":22,\"del\":10}]")
            .contains("\"state\":\"error\"")
            .contains("\"badge\":\"待确认\"");
    }

    private Long submit(Long accountId, Long conversationId, String instruction) {
        return workspace.submit(accountId, conversationId,
            new com.prelude.resume.api.ResumeInstructionRequest(instruction, List.of(), List.of())).id();
    }

    private void claim(CountDownLatch ready, CountDownLatch go, Long turnId, AtomicInteger wins) {
        ready.countDown();
        try {
            go.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        if (repository.claimTurn(turnId, LocalDateTime.now())) {
            wins.incrementAndGet();
        }
    }

    private ResumeAssistantRunner failingRunner() {
        // A runner built over a conversation with no resume fails before any model call, which
        // is the cheapest real failure this path can produce.
        return new ResumeAssistantRunner(
            repository,
            org.mockito.Mockito.mock(com.prelude.resume.application.repository.ResumeTranscriptRepository.class),
            org.mockito.Mockito.mock(com.prelude.resume.application.repository.ResumeDocumentRepository.class),
            org.mockito.Mockito.mock(com.prelude.resume.application.port.ResumeRepository.class),
            org.mockito.Mockito.mock(com.prelude.assets.api.AttachmentContextPort.class),
            org.mockito.Mockito.mock(com.prelude.llm.api.LlmPort.class),
            org.mockito.Mockito.mock(com.prelude.llm.api.PromptRegistry.class),
            org.mockito.Mockito.mock(ResumePolicy.class),
            new tools.jackson.databind.ObjectMapper());
    }

    private LocalDateTime pinnedAt(long conversationId) {
        List<LocalDateTime> rows = jdbc.queryForList(
            "SELECT pinned_at FROM resume_conversation WHERE id = ?", LocalDateTime.class, conversationId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String status(long turnId) {
        return jdbc.queryForObject("SELECT status FROM resume_turn WHERE id = ?", String.class, turnId);
    }

    private String failureReason(long turnId) {
        return jdbc.queryForObject(
            "SELECT failure_reason FROM resume_turn WHERE id = ?", String.class, turnId);
    }
}
