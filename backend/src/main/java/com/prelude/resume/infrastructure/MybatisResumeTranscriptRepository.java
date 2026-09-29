package com.prelude.resume.infrastructure;

import com.prelude.resume.application.repository.ResumeTranscriptRepository;
import com.prelude.resume.domain.ResumeAgentRun;
import com.prelude.resume.domain.ResumeAgentStep;
import com.prelude.resume.domain.ResumePatchProposal;
import com.prelude.resume.infrastructure.persistence.ResumeAgentRunEntity;
import com.prelude.resume.infrastructure.persistence.ResumeAgentRunMapper;
import com.prelude.resume.infrastructure.persistence.ResumeAgentStepEntity;
import com.prelude.resume.infrastructure.persistence.ResumeAgentStepMapper;
import com.prelude.resume.infrastructure.persistence.ResumeAssistantMessageEntity;
import com.prelude.resume.infrastructure.persistence.ResumeAssistantMessageMapper;
import com.prelude.resume.infrastructure.persistence.ResumePatchProposalEntity;
import com.prelude.resume.infrastructure.persistence.ResumePatchProposalMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisResumeTranscriptRepository implements ResumeTranscriptRepository {

    private final ResumeAssistantMessageMapper messages;
    private final ResumeAgentRunMapper runs;
    private final ResumeAgentStepMapper steps;
    private final ResumePatchProposalMapper proposals;
    private final ResumeCodec codec;

    public MybatisResumeTranscriptRepository(
        ResumeAssistantMessageMapper messages,
        ResumeAgentRunMapper runs,
        ResumeAgentStepMapper steps,
        ResumePatchProposalMapper proposals,
        ResumeCodec codec
    ) {
        this.messages = messages;
        this.runs = runs;
        this.steps = steps;
        this.proposals = proposals;
        this.codec = codec;
    }

    @Override
    public Long appendMessage(Long turnId, Long conversationId, String content, LocalDateTime now) {
        ResumeAssistantMessageEntity row = new ResumeAssistantMessageEntity();
        row.setTurnId(turnId);
        row.setConversationId(conversationId);
        row.setSeqNum(messages.maxSeq(turnId) + 1);
        row.setContent(content);
        row.setCreatedAt(now);
        messages.insert(row);
        return row.getId();
    }

    @Override
    public List<AssistantMessage> listMessages(Collection<Long> turnIds) {
        if (turnIds.isEmpty()) {
            return List.of();
        }
        return messages.listByTurns(turnIds).stream()
            .map(row -> new AssistantMessage(
                row.getId(), row.getTurnId(), row.getContent(), row.getCreatedAt()))
            .toList();
    }

    @Override
    public Long openRun(ResumeAgentRun run) {
        ResumeAgentRunEntity row = new ResumeAgentRunEntity();
        row.setTurnId(run.turnId());
        row.setConversationId(run.conversationId());
        row.setResumeId(run.resumeId());
        row.setBaseRevision(run.baseRevision());
        row.setModelExecutionSnapshotId(run.modelExecutionSnapshotId());
        row.setPromptId(run.promptId());
        row.setStatus(run.status().wire());
        row.setStartedAt(run.startedAt());
        runs.insert(row);
        return row.getId();
    }


    @Override
    public Optional<ResumeAgentRun> findRun(Long runId) {
        return Optional.ofNullable(runs.selectById(runId)).map(this::toRun);
    }

    @Override
    public Optional<ResumeAgentRun> findRunByTurn(Long turnId) {
        return Optional.ofNullable(runs.findByTurn(turnId)).map(this::toRun);
    }

    @Override
    public List<ResumeAgentRun> listRuns(Collection<Long> turnIds) {
        if (turnIds.isEmpty()) {
            return List.of();
        }
        return runs.listByTurns(turnIds).stream().map(this::toRun).toList();
    }

    @Override
    public void finishRun(
        Long runId,
        ResumeAgentRun.Status status,
        String failureReason,
        LocalDateTime now
    ) {
        runs.finish(runId, status.wire(), failureReason, now);
    }

    @Override
    public void appendStep(ResumeAgentStep step) {
        steps.insert(toStepEntity(step));
    }

    @Override
    public List<ResumeAgentStep> listSteps(Collection<Long> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        return steps.listByRuns(runIds).stream().map(this::toStep).toList();
    }

    @Override
    public int nextStepOrder(Long runId) {
        return steps.maxSortOrder(runId) + 1;
    }

    @Override
    public Long recordProposal(ResumePatchProposal proposal) {
        ResumePatchProposalEntity row = toProposalEntity(proposal);
        proposals.insert(row);
        return row.getId();
    }

    @Override
    public Optional<ResumePatchProposal> findProposal(Long proposalId) {
        return Optional.ofNullable(proposals.selectById(proposalId)).map(this::toProposal);
    }

    @Override
    public List<ResumePatchProposal> listProposals(Collection<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        return proposals.listByConversations(conversationIds).stream().map(this::toProposal).toList();
    }

    @Override
    public boolean decide(
        Long proposalId,
        ResumePatchProposal.Status status,
        String decisionNote,
        Long decidedByAccountId,
        LocalDateTime now
    ) {
        return proposals.decide(proposalId, status.wire(), decisionNote, decidedByAccountId, now) == 1;
    }

    @Override
    public List<ResumePatchProposal> listPendingProposalsBehindRevision(Long resumeId, int revision) {
        return proposals.listStaleCandidates(resumeId, revision).stream().map(this::toProposal).toList();
    }

    private ResumeAgentStepEntity toStepEntity(ResumeAgentStep step) {
        ResumeAgentStepEntity row = new ResumeAgentStepEntity();
        row.setId(step.id());
        row.setRunId(step.runId());
        row.setSortOrder(step.sortOrder());
        row.setKind(step.kind().wire());
        row.setLabel(step.label());
        row.setToolName(step.toolName() == null ? "" : step.toolName());
        row.setArgumentsJson(step.argumentsJson());
        row.setResultExcerpt(step.resultExcerpt() == null ? "" : step.resultExcerpt());
        row.setFilesJson(codec.writeFiles(step.files()));
        row.setChipsJson(codec.writeList(step.chips()));
        row.setDetailJson(codec.writeList(step.detail()));
        row.setBadge(step.badge());
        row.setBadgeTone(step.badgeTone() == null ? null : step.badgeTone().wire());
        row.setState(step.state().wire());
        row.setError(step.error());
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private ResumePatchProposalEntity toProposalEntity(ResumePatchProposal proposal) {
        ResumePatchProposalEntity row = new ResumePatchProposalEntity();
        row.setRunId(proposal.runId());
        row.setResumeId(proposal.resumeId());
        row.setConversationId(proposal.conversationId());
        row.setBaseRevision(proposal.baseRevision());
        row.setAffectedBlockIds(codec.writeList(proposal.affectedBlockIds()));
        row.setReason(proposal.reason());
        row.setOperationsJson(codec.writeOperations(proposal.operations()));
        row.setFactRiskJson(codec.writeFactRisk(proposal.factRisk()));
        row.setStatus(proposal.status().wire());
        row.setCreatedAt(proposal.createdAt());
        return row;
    }

    private ResumeAgentRun toRun(ResumeAgentRunEntity row) {
        return new ResumeAgentRun(
            row.getId(),
            row.getTurnId(),
            row.getConversationId(),
            row.getResumeId(),
            row.getBaseRevision(),
            row.getModelExecutionSnapshotId(),
            row.getPromptId(),
            ResumeAgentRun.Status.fromWire(row.getStatus()),
            row.getFailureReason(),
            row.getStartedAt(),
            row.getFinishedAt()
        );
    }

    private ResumeAgentStep toStep(ResumeAgentStepEntity row) {
        return new ResumeAgentStep(
            row.getId(),
            row.getRunId(),
            row.getSortOrder(),
            ResumeAgentStep.Kind.fromWire(row.getKind()),
            row.getLabel(),
            row.getToolName(),
            row.getArgumentsJson(),
            row.getResultExcerpt(),
            codec.readFiles(row.getFilesJson()),
            codec.readStringList(row.getChipsJson()),
            codec.readStringList(row.getDetailJson()),
            row.getBadge(),
            row.getBadgeTone() == null ? null : ResumeAgentStep.BadgeTone.fromWire(row.getBadgeTone()),
            ResumeAgentStep.State.fromWire(row.getState()),
            row.getError()
        );
    }

    private ResumePatchProposal toProposal(ResumePatchProposalEntity row) {
        return new ResumePatchProposal(
            row.getId(),
            row.getRunId(),
            row.getResumeId(),
            row.getConversationId(),
            row.getBaseRevision(),
            codec.readStringList(row.getAffectedBlockIds()),
            row.getReason(),
            codec.readOperations(row.getOperationsJson()),
            codec.readFactRisk(row.getFactRiskJson()),
            ResumePatchProposal.Status.fromWire(row.getStatus()),
            row.getDecisionNote(),
            row.getDecidedAt(),
            row.getDecidedByAccountId(),
            row.getCreatedAt()
        );
    }
}
