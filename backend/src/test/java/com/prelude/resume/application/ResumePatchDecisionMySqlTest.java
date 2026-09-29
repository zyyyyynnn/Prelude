package com.prelude.resume.application;

import com.prelude.resume.api.ResumeProposalResponse;
import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.test.ResumeDataFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one door from a model's patch to the resume document, against real MySQL.
 *
 * <p>What is asserted here is the shape of the guarantee: a patch applies only through a
 * decision, only once, and only onto the revision it was written against. Each of those is a
 * conditional statement or a comparison against stored state, so all three are checked where
 * they are actually enforced.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "prelude.jobs.scheduling-enabled=false"
})
class ResumePatchDecisionMySqlTest {

    private static final String REPLACE_SKILL = """
        [{"kind":"replace","blockId":"技能-1","text":"Java（三年生产经验）"}]""";

    @Autowired
    private ResumePatchDecisionService decisions;

    @Autowired
    private ResumeWorkspaceService workspace;

    @Autowired
    private ResumeDocumentRepository documents;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("接受提案写出一个新版本，文档里只有被点名的块变了")
    void acceptingAppliesThePatchAsANewRevision() {
        var fixture = fixture(REPLACE_SKILL, "pending");

        ResumeProposalResponse accepted = decisions.accept(fixture.accountId(), fixture.proposalId());

        assertThat(accepted.status()).isEqualTo("accepted");
        assertThat(accepted.resultingRevisionId()).isNotNull();
        var revision = documents.latest(fixture.resumeId()).orElseThrow();
        assertThat(revision.revisionNumber()).isEqualTo(fixture.revision() + 1);
        assertThat(revision.origin()).isEqualTo(
            com.prelude.resume.domain.ResumeRevision.Origin.AGENT_PATCH);
        assertThat(revision.document().block("技能-1"))
            .get().extracting(com.prelude.resume.domain.ResumeDocument.Block::text)
            .isEqualTo("Java（三年生产经验）");
        assertThat(revision.document().block("技能-2"))
            .get().extracting(com.prelude.resume.domain.ResumeDocument.Block::text)
            .isEqualTo("MySQL");
    }

    @Test
    @DisplayName("连点两次接受只产生一个版本")
    void aSecondAcceptIsRefused() {
        var fixture = fixture(REPLACE_SKILL, "pending");
        decisions.accept(fixture.accountId(), fixture.proposalId());

        assertThatThrownBy(() -> decisions.accept(fixture.accountId(), fixture.proposalId()))
            .hasMessageContaining("已经处理过");
        assertThat(revisionCount(fixture.resumeId())).isEqualTo(2);
    }

    @Test
    @DisplayName("基线被超过的提案标为失效，绝不把补丁重放到更新的文档上")
    void aProposalBehindTheDocumentGoesStale() {
        var fixture = fixture(REPLACE_SKILL, "pending");
        // Something else moves the document first: a user edit landing as the next revision.
        documents.append(new com.prelude.resume.domain.ResumeRevision(
            null, fixture.resumeId(), fixture.revision() + 1,
            documents.latest(fixture.resumeId()).orElseThrow().document(),
            "候选人自己改过", com.prelude.resume.domain.ResumeRevision.Origin.USER_EDIT,
            null, java.time.LocalDateTime.now()));

        assertThatThrownBy(() -> decisions.accept(fixture.accountId(), fixture.proposalId()))
            .hasMessageContaining("已失效");
        assertThat(proposalStatus(fixture.proposalId())).isEqualTo("stale");
        assertThat(revisionCount(fixture.resumeId())).isEqualTo(2);
    }

    @Test
    @DisplayName("拒绝留下决定，不动文档")
    void rejectingLeavesTheDocumentAlone() {
        var fixture = fixture(REPLACE_SKILL, "pending");

        ResumeProposalResponse rejected = decisions.reject(fixture.accountId(), fixture.proposalId());

        assertThat(rejected.status()).isEqualTo("rejected");
        assertThat(rejected.decidable()).isFalse();
        assertThat(revisionCount(fixture.resumeId())).isEqualTo(1);
    }

    @Test
    @DisplayName("审阅面按当前文档给出每一块的改前改后")
    void theReviewSurfaceShowsBeforeAndAfter() {
        var fixture = fixture(
            "[{\"kind\":\"replace\",\"blockId\":\"项目-1-描述\",\"text\":\"接口 P99 从 480ms 降到 90ms\"}]",
            "pending");

        ResumeProposalResponse view = workspace.proposal(fixture.accountId(), fixture.proposalId());

        assertThat(view.decidable()).isTrue();
        assertThat(view.changes()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo("replace");
            assertThat(change.blockId()).isEqualTo("项目-1-描述");
            assertThat(change.before()).isEqualTo("支撑日均 200 万单");
            assertThat(change.after()).contains("90ms");
        });
        // Facts are judged when the candidate arrives, not when it is read back: see
        // ResumeAssistantRunnerTest, where an unsupported number is named on the proposal.
    }

    private Scenario fixture(String operations, String status) {
        var workspaceFixture = ResumeDataFixtures.workspace(jdbc, "decision");
        long proposalId = ResumeDataFixtures.proposal(
            jdbc, workspaceFixture, workspaceFixture.revision(), operations, status);
        return new Scenario(workspaceFixture.accountId(), workspaceFixture.resumeId(),
            workspaceFixture.revision(), proposalId);
    }

    private long revisionCount(long resumeId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM resume_revision WHERE resume_id = ?", Long.class, resumeId);
    }

    private String proposalStatus(long proposalId) {
        return jdbc.queryForObject(
            "SELECT status FROM resume_patch_proposal WHERE id = ?", String.class, proposalId);
    }

    private record Scenario(long accountId, long resumeId, int revision, long proposalId) {
    }
}
