package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.resume.api.ResumeAssistantMessageResponse;
import com.prelude.resume.api.ResumeConversationResponse;
import com.prelude.resume.api.ResumeConversationStatus;
import com.prelude.resume.api.ResumeDocumentResponse;
import com.prelude.resume.api.ResumeInstructionRequest;
import com.prelude.resume.api.ResumeToolGroupResponse;
import com.prelude.resume.api.ResumeToolStepResponse;
import com.prelude.resume.api.ResumeProposalResponse;
import com.prelude.resume.api.ResumeTurnResponse;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.resume.application.repository.ResumeTranscriptRepository;
import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeAgentRun;
import com.prelude.resume.domain.ResumeAgentStep;
import com.prelude.resume.domain.ResumeConversation;
import com.prelude.resume.domain.ResumePatchProposal;
import com.prelude.resume.domain.ResumeRevision;
import com.prelude.resume.domain.ResumeTurn;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The resume workspace as the candidate sees it: conversations and their turns, the trace
 * each run left behind, the proposal awaiting a decision, and the document a patch would
 * change.
 *
 * <p>Submitting a turn only enqueues it; the drain runs it. A turn therefore has exactly one
 * owner at any moment, and the route a turn takes after a restart is the route it takes
 * every time. Nothing here reaches into persistence: the module's repositories and the ports
 * it declares are the only collaborators.
 */
@Service
@RequiredArgsConstructor
public class ResumeWorkspaceService {

    private static final String UNTITLED = "新的简历工作";

    private final ResumeWorkspaceRepository workspace;
    private final ResumeTranscriptRepository transcript;
    private final ResumeDocumentRepository documents;
    private final ResumeRepository resumes;
    private final AttachmentContextPort attachments;
    private final ResumePatchDecisionService decisions;

    public List<ResumeConversationResponse> listConversations(Long accountId) {
        List<ResumeConversation> conversations = workspace.listConversations(accountId);
        if (conversations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = conversations.stream().map(ResumeConversation::id).toList();
        var active = workspace.conversationIdsWithPendingWork(ids);
        var withTurns = workspace.conversationIdsWithTurns(ids);
        return conversations.stream()
            .map(conversation -> toConversation(conversation,
                active.contains(conversation.id()) || !withTurns.contains(conversation.id())
                    ? ResumeConversationStatus.ACTIVE
                    : ResumeConversationStatus.FINISHED))
            .toList();
    }

    @Transactional
    public ResumeConversationResponse createConversation(Long accountId, Long resumeId) {
        if (resumeId != null) {
            resumes.findById(resumeId)
                .filter(stored -> stored.accountId().equals(accountId))
                .orElseThrow(() -> BusinessException.notFound("简历不存在"));
        }
        ResumeConversation conversation = ResumeConversation.create(accountId, resumeId, UNTITLED, LocalDateTime.now());
        Long id = workspace.createConversation(conversation);
        return toConversation(conversation.withId(id), ResumeConversationStatus.ACTIVE);
    }

    @Transactional
    public void pinConversation(Long accountId, Long conversationId, boolean pinned) {
        requireConversation(accountId, conversationId);
        workspace.setPinned(accountId, conversationId, pinned, LocalDateTime.now());
    }

    @Transactional
    public void deleteConversation(Long accountId, Long conversationId) {
        requireConversation(accountId, conversationId);
        attachments.unbind(accountId, "resume", conversationId);
        workspace.deleteConversation(accountId, conversationId);
    }

    public List<ResumeTurnResponse> listTurns(Long accountId, Long conversationId) {
        requireConversation(accountId, conversationId);
        List<ResumeTurn> turns = workspace.listTurns(conversationId);
        if (turns.isEmpty()) {
            return List.of();
        }

        List<Long> turnIds = turns.stream().map(ResumeTurn::id).toList();
        Map<Long, ResumeAgentRun> runByTurn = transcript.listRuns(turnIds).stream()
            .collect(Collectors.toMap(ResumeAgentRun::turnId, Function.identity(), (first, last) -> last));
        List<Long> runIds = List.copyOf(runByTurn.values().stream().map(ResumeAgentRun::id).toList());
        Map<Long, List<ResumeAgentStep>> stepsByRun = transcript.listSteps(runIds).stream()
            .collect(Collectors.groupingBy(ResumeAgentStep::runId));
        Map<Long, List<ResumeTranscriptRepository.AssistantMessage>> messagesByTurn =
            transcript.listMessages(turnIds).stream()
                .collect(Collectors.groupingBy(ResumeTranscriptRepository.AssistantMessage::turnId));

        return turns.stream()
            .map(turn -> toTurn(turn,
                messagesByTurn.getOrDefault(turn.id(), List.of()),
                runByTurn.get(turn.id()),
                stepsByRun))
            .toList();
    }

    /**
     * Enqueue one instruction and report where it stands. Execution belongs to the drain:
     * one consumer for the whole queue means a submitted turn has exactly one owner, and the
     * path a turn takes after a restart is the path it takes every time.
     */
    public ResumeTurnResponse submit(
        Long accountId,
        Long conversationId,
        ResumeInstructionRequest request
    ) {
        requireConversation(accountId, conversationId);
        if (!request.attachmentIds().isEmpty()) {
            attachments.requireOwned(accountId, request.attachmentIds());
        }
        ResumeTurn queued = ResumeTurn.queued(
            conversationId, accountId, request.instruction().trim(),
            request.blockIds(), request.attachmentIds(), 0, LocalDateTime.now());
        Long turnId = workspace.enqueueTurn(queued);
        return toTurn(workspace.findTurn(accountId, turnId).orElse(queued),
            List.of(), null, Map.of());
    }

    /**
     * Withdraw a turn that has not started. A turn already in flight is left alone: aborting
     * a model call mid-way is not implemented, and pretending otherwise would report a
     * cancellation that changed nothing.
     */
    @Transactional
    public void cancel(Long accountId, Long conversationId, Long turnId) {
        requireConversation(accountId, conversationId);
        ResumeTurn turn = workspace.findTurn(accountId, turnId)
            .orElseThrow(() -> BusinessException.notFound("指令轮不存在"));
        if (!turn.conversationId().equals(conversationId)) {
            throw BusinessException.notFound("指令轮不存在");
        }
        if (turn.status() != ResumeTurn.Status.QUEUED) {
            throw BusinessException.conflict("resume_turn_running", "这条指令已经在执行，无法撤回");
        }
        if (!workspace.cancelQueuedTurn(turnId, LocalDateTime.now())) {
            throw BusinessException.conflict("resume_turn_running", "这条指令已经在执行，无法撤回");
        }
    }

    /** The document the workspace previews and selects blocks from. */
    public ResumeDocumentResponse document(Long accountId, Long conversationId) {
        ResumeConversation conversation = requireConversation(accountId, conversationId);
        if (conversation.resumeId() == null) {
            return new ResumeDocumentResponse(null, 0, List.of());
        }
        ResumeRevision latest = documents.latest(conversation.resumeId()).orElse(null);
        return new ResumeDocumentResponse(
            conversation.resumeId(),
            latest == null ? 0 : latest.revisionNumber(),
            latest == null
                ? List.of()
                : latest.document().blocks().stream()
                    .map(block -> new ResumeDocumentResponse.Block(
                        block.id(), block.section(), block.kind(), block.text()))
                    .toList());
    }

    public ResumeProposalResponse proposal(Long accountId, Long proposalId) {
        ResumePatchProposal proposal = transcript.findProposal(proposalId)
            .orElseThrow(() -> BusinessException.notFound("提案不存在"));
        requireConversation(accountId, proposal.conversationId());
        return decisions.view(proposal);
    }

    /** Proposals awaiting a decision, newest first — read by the decision surface, not the stream. */
    public List<ResumeProposalResponse> proposals(Long accountId, Long conversationId) {
        requireConversation(accountId, conversationId);
        return transcript.listProposals(List.of(conversationId)).stream()
            .map(decisions::view)
            .toList();
    }

    private ResumeConversation requireConversation(Long accountId, Long conversationId) {
        return workspace.findConversation(accountId, conversationId)
            .orElseThrow(() -> BusinessException.notFound("会话不存在"));
    }

    private ResumeTurnResponse toTurn(
        ResumeTurn turn,
        List<ResumeTranscriptRepository.AssistantMessage> messages,
        ResumeAgentRun run,
        Map<Long, List<ResumeAgentStep>> stepsByRun
    ) {
        return new ResumeTurnResponse(
            turn.id(),
            turn.instruction(),
            turn.status().wire(),
            turn.createdAt(),
            turn.startedAt(),
            turn.completedAt(),
            turn.failureReason(),
            messages.stream()
                .map(message -> toMessage(message, run, stepsByRun))
                .toList());
    }

    private ResumeAssistantMessageResponse toMessage(
        ResumeTranscriptRepository.AssistantMessage message,
        ResumeAgentRun run,
        Map<Long, List<ResumeAgentStep>> stepsByRun
    ) {
        List<ResumeAgentStep> steps = run == null
            ? List.of()
            : stepsByRun.getOrDefault(run.id(), List.of());
        return new ResumeAssistantMessageResponse(
            message.id(),
            message.turnId(),
            message.content(),
            message.createdAt(),
            run == null || steps.isEmpty() ? null : toGroup(run, steps));
    }

    /**
     * The trace header, counted from the steps that actually ran and spelled with the words the
     * workspace already uses for a run: a kind that did not happen is left out rather than
     * rounded into a sentence, and nothing trails an ellipsis that no truncation produced.
     */
    private static String summaryOf(List<ResumeAgentStep> steps) {
        java.util.Map<ResumeAgentStep.Kind, Long> counts = steps.stream()
            .collect(Collectors.groupingBy(ResumeAgentStep::kind, Collectors.counting()));
        List<String> thoughts = new java.util.ArrayList<>();
        List<String> actions = new java.util.ArrayList<>();
        appendIfPositive(thoughts, counts.get(ResumeAgentStep.Kind.THINK), "思考 %d轮");
        appendIfPositive(actions, counts.get(ResumeAgentStep.Kind.READ), "读%d次文件");
        appendIfPositive(actions, counts.get(ResumeAgentStep.Kind.WRITE), "改%d次文件");
        appendIfPositive(actions, counts.get(ResumeAgentStep.Kind.SEARCH), "查找%d次");
        appendIfPositive(actions, counts.get(ResumeAgentStep.Kind.RUN), "执行%d次命令");
        if (actions.isEmpty()) {
            return String.join(" · ", thoughts);
        }
        if (thoughts.isEmpty()) {
            return String.join("、", actions);
        }
        return String.join(" · ", thoughts) + " · " + String.join("、", actions);
    }

    private static void appendIfPositive(List<String> into, Long count, String format) {
        if (count != null && count > 0) {
            into.add(String.format(format, count));
        }
    }

    /**
     * The trace header counts what really happened in the run: how many actions it took and how
     * many of those were tool calls the model itself initiated.
     */
    private ResumeToolGroupResponse toGroup(ResumeAgentRun run, List<ResumeAgentStep> steps) {
        return new ResumeToolGroupResponse(
            run.id(),
            summaryOf(steps),
            run.status() == ResumeAgentRun.Status.RUNNING ? "running" : "done",
            steps.stream().map(this::toStep).toList());
    }

    /** The run's step vocabulary spelled in the icons the stream already renders. */
    private static String icon(ResumeAgentStep.Kind kind) {
        return switch (kind) {
            case THINK -> "think";
            case READ -> "read";
            case SEARCH -> "search";
            case WRITE -> "write";
            case RUN -> "run";
            case POLICY -> "find";
            case PROPOSAL -> "edit";
        };
    }

    private ResumeToolStepResponse toStep(ResumeAgentStep step) {
        return new ResumeToolStepResponse(
            step.id(),
            icon(step.kind()),
            step.label(),
            step.badge(),
            step.badgeTone() == null ? null : step.badgeTone().wire(),
            step.chips(),
            step.detail(),
            step.files().stream()
                .map(file -> new ResumeToolStepResponse.FileDiff(file.name(), file.add(), file.del()))
                .toList(),
            step.state().wire(),
            step.error());
    }

    private ResumeConversationResponse toConversation(
        ResumeConversation conversation,
        ResumeConversationStatus status
    ) {
        return new ResumeConversationResponse(
            conversation.id(),
            conversation.title(),
            conversation.resumeId(),
            conversation.updatedAt(),
            conversation.pinned(),
            status.wire());
    }
}
