package com.prelude.artifact.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.artifact.application.GenerateInterviewReport.GenerationResult;
import com.prelude.artifact.application.GenerateInterviewReport.Outcome;
import com.prelude.artifact.domain.AccountWeakness;
import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.infrastructure.persistence.InterviewSessionMapper;
import com.prelude.jobs.BackgroundJobRecoveryService;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.persistence.BackgroundJob;
import com.prelude.jobs.persistence.BackgroundJobMapper;
import com.prelude.llm.ModelCapabilityCatalog;
import com.prelude.llm.persistence.ModelExecutionSnapshot;
import com.prelude.llm.persistence.ModelExecutionSnapshotMapper;
import com.prelude.llm.persistence.ModelProfile;
import com.prelude.llm.persistence.ModelProfileMapper;
import com.prelude.resume.infrastructure.persistence.Resume;
import com.prelude.resume.infrastructure.persistence.ResumeMapper;
import com.prelude.template.domain.PositionTemplate;
import com.prelude.template.infrastructure.persistence.PositionTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class ReportJobCompletionMySqlTest {

    @Autowired
    private ReportJobCompletion completion;

    @Autowired
    private BackgroundJobOperations jobs;

    @Autowired
    private BackgroundJobRecoveryService recoveryService;

    @Autowired
    private BackgroundJobMapper jobMapper;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private PositionTemplateMapper positionTemplateMapper;

    @Autowired
    private ModelProfileMapper profileMapper;

    @Autowired
    private ModelExecutionSnapshotMapper snapshotMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleAttemptCannotCommitReportStateAfterReplacementClaim() {
        Fixture fixture = createFixture();
        BackgroundJobRef ref = request(fixture.accountId(), fixture.sessionId());
        int attemptOne = jobs.claim(ref.jobId()).attemptNumber();
        expireLease(ref.jobId());
        assertThat(recoveryService.recover(ref.jobId(), LocalDateTime.now()))
            .isEqualTo(BackgroundJobRecoveryService.RecoveryOutcome.RETRY_SCHEDULED);
        int attemptTwo = jobs.claim(ref.jobId()).attemptNumber();

        assertThat(completion.complete(
            ref.jobId(), attemptOne, fixture.sessionId(), generated(fixture))).isFalse();

        InterviewSession afterStaleWorker = sessionMapper.selectById(fixture.sessionId());
        BackgroundJob replacement = storedJob(ref.jobId());
        assertThat(afterStaleWorker.getStatus()).isEqualTo("generating");
        assertThat(afterStaleWorker.getSummaryReport()).isNull();
        assertThat(replacement.getStatus()).isEqualTo(BackgroundJob.RUNNING);
        assertThat(replacement.getAttemptCount()).isEqualTo(attemptTwo);
        assertThat(scoreRows(fixture.sessionId())).isZero();
        assertThat(weaknessRows(fixture.sessionId())).isZero();

        assertThat(completion.complete(
            ref.jobId(), attemptTwo, fixture.sessionId(), generated(fixture))).isTrue();

        InterviewSession finished = sessionMapper.selectById(fixture.sessionId());
        BackgroundJob succeeded = storedJob(ref.jobId());
        assertThat(finished.getStatus()).isEqualTo("finished");
        assertThat(finished.getSummaryReport()).isEqualTo("{\"report\":\"ready\"}");
        assertThat(succeeded.getStatus()).isEqualTo(BackgroundJob.SUCCEEDED);
        assertThat(scoreRows(fixture.sessionId())).isEqualTo(1);
        assertThat(weaknessRows(fixture.sessionId())).isEqualTo(1);
    }

    @Test
    void failedDomainFinalizationRollsBackTheJobSuccessTransition() {
        Fixture fixture = createFixture();
        BackgroundJobRef ref = request(fixture.accountId(), fixture.sessionId());
        int attemptNumber = jobs.claim(ref.jobId()).attemptNumber();

        InterviewSession invalid = sessionMapper.selectById(fixture.sessionId());
        invalid.setStatus("ongoing");
        sessionMapper.updateById(invalid);

        assertThatThrownBy(() -> completion.complete(
            ref.jobId(), attemptNumber, fixture.sessionId(), generated(fixture)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("lost generating state");

        BackgroundJob job = storedJob(ref.jobId());
        InterviewSession session = sessionMapper.selectById(fixture.sessionId());
        assertThat(job.getStatus()).isEqualTo(BackgroundJob.RUNNING);
        assertThat(job.getAttemptCount()).isEqualTo(attemptNumber);
        assertThat(session.getStatus()).isEqualTo("ongoing");
        assertThat(session.getSummaryReport()).isNull();
        assertThat(scoreRows(fixture.sessionId())).isZero();
        assertThat(weaknessRows(fixture.sessionId())).isZero();
    }

    private Fixture createFixture() {
        Account account = new Account();
        account.setUsername("report-atomic-" + System.nanoTime());
        account.setRevision(0L);
        accountMapper.insert(account);

        Resume resume = new Resume();
        resume.setAccountId(account.getId());
        resume.setFileName("resume-" + System.nanoTime() + ".pdf");
        resume.setRawText("resume");
        resume.setParsedSkills("[]");
        resume.setParsedProjects("[]");
        resumeMapper.insert(resume);

        PositionTemplate position = new PositionTemplate();
        position.setAccountId(account.getId());
        position.setName("position-" + System.nanoTime());
        position.setSystemPrompt("system");
        positionTemplateMapper.insert(position);

        ModelProfile profile = new ModelProfile();
        profile.setAccountId(account.getId());
        profile.setProvider(ModelCapabilityCatalog.PROVIDER_DEEPSEEK);
        profile.setModel("deepseek-v4-pro");
        profile.setReasoningLevel("AUTO");
        profile.setEffectiveParametersJson("{\"maxOutputTokens\":4096}");
        profile.setFallbackModelsJson("[]");
        profileMapper.insert(profile);

        ModelExecutionSnapshot snapshot = new ModelExecutionSnapshot();
        snapshot.setAccountId(account.getId());
        snapshot.setProfileId(profile.getId());
        snapshot.setProvider(ModelCapabilityCatalog.PROVIDER_DEEPSEEK);
        snapshot.setModel("deepseek-v4-pro");
        snapshot.setReasoningLevel("AUTO");
        snapshot.setEffectiveParametersJson("{\"maxOutputTokens\":4096}");
        snapshot.setCapabilityVersion(ModelCapabilityCatalog.CAPABILITY_VERSION);
        snapshot.setFallbackModelsJson("[]");
        snapshotMapper.insert(snapshot);

        InterviewSession session = new InterviewSession();
        session.setAccountId(account.getId());
        session.setResumeId(resume.getId());
        session.setPositionId(position.getId());
        session.setTargetPosition(position.getName());
        session.setModelExecutionSnapshotId(snapshot.getId());
        session.setStatus("generating");
        sessionMapper.insert(session);
        return new Fixture(account.getId(), session.getId());
    }

    private BackgroundJobRef request(long accountId, long sessionId) {
        return jobs.request(new BackgroundJobRequest(
            "report.generate",
            accountId,
            sessionId,
            "report.generate:atomic:" + System.nanoTime(),
            "{}"
        ));
    }

    private GenerationResult generated(Fixture fixture) {
        ScoreHistory score = new ScoreHistory();
        score.setAccountId(fixture.accountId());
        score.setSessionId(fixture.sessionId());
        score.setTechnicalScore(8);
        score.setExpressionScore(7);
        score.setLogicScore(9);

        AccountWeakness weakness = new AccountWeakness();
        weakness.setAccountId(fixture.accountId());
        weakness.setSessionId(fixture.sessionId());
        weakness.setCategory("system-design");
        weakness.setDescription("needs stronger capacity evidence");
        return new GenerationResult(
            Outcome.GENERATED,
            "{\"report\":\"ready\"}",
            score,
            List.of(weakness)
        );
    }

    private void expireLease(String jobId) {
        BackgroundJob job = storedJob(jobId);
        job.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        jobMapper.updateById(job);
    }

    private BackgroundJob storedJob(String jobId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
    }

    private long scoreRows(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM score_history WHERE session_id = ?", Long.class, sessionId);
        return count == null ? 0 : count;
    }

    private long weaknessRows(long sessionId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account_weakness WHERE session_id = ?", Long.class, sessionId);
        return count == null ? 0 : count;
    }

    private record Fixture(long accountId, long sessionId) {
    }
}
