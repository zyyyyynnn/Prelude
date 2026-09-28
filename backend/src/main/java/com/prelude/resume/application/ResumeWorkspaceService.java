package com.prelude.resume.application;

import com.prelude.BusinessException;
import com.prelude.resume.api.ResumeAssistantMessageResponse;
import com.prelude.resume.api.ResumeConversationResponse;
import com.prelude.resume.api.ResumeToolStepResponse;
import com.prelude.resume.api.ResumeToolGroupResponse;
import com.prelude.resume.api.ResumeTurnResponse;
import com.prelude.resume.api.port.ResumeConversationStatus;
import com.prelude.resume.api.port.ResumeToolState;
import com.prelude.resume.api.port.ResumeTurnStatus;
import com.prelude.resume.infrastructure.persistence.ResumeAssistantMessageEntity;
import com.prelude.resume.infrastructure.persistence.ResumeAssistantMessageMapper;
import com.prelude.resume.infrastructure.persistence.ResumeConversationEntity;
import com.prelude.resume.infrastructure.persistence.ResumeConversationMapper;
import com.prelude.resume.infrastructure.persistence.ResumeToolCallEntity;
import com.prelude.resume.infrastructure.persistence.ResumeToolCallMapper;
import com.prelude.resume.infrastructure.persistence.ResumeToolDiffEntity;
import com.prelude.resume.infrastructure.persistence.ResumeToolDiffMapper;
import com.prelude.resume.infrastructure.persistence.ResumeToolGroupEntity;
import com.prelude.resume.infrastructure.persistence.ResumeToolGroupMapper;
import com.prelude.resume.infrastructure.persistence.ResumeTurnEntity;
import com.prelude.resume.infrastructure.persistence.ResumeTurnMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resume workspace conversations: one user instruction is a turn, each assistant
 * message may own one tool-call group. The assistant run here is a controlled
 * integration that writes real rows and emits real events — the product path never
 * fabricates a stream on the client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeWorkspaceService {

    private final ResumeConversationMapper conversations;
    private final ResumeTurnMapper turns;
    private final ResumeAssistantMessageMapper messages;
    private final ResumeToolGroupMapper toolGroups;
    private final ResumeToolCallMapper toolCalls;
    private final ResumeToolDiffMapper toolDiffs;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResumeConversationResponse createConversation(Long accountId, Long resumeId) {
        ResumeConversationEntity entity = new ResumeConversationEntity();
        entity.setAccountId(accountId);
        entity.setResumeId(resumeId);
        entity.setTitle("新的简历工作");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        conversations.insert(entity);
        return toConversation(entity, ResumeConversationStatus.ACTIVE);
    }

    /**
     * The sidebar list. A conversation's group is derived from its turns — active while a
     * turn is queued or running or none has been sent yet, finished once every turn is
     * done — so the list needs no status column of its own to keep.
     */
    public List<ResumeConversationResponse> listConversations(Long accountId) {
        List<ResumeConversationEntity> rows = conversations.listByOwner(accountId);
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(ResumeConversationEntity::getId).toList();
        Set<Long> activeIds = Set.copyOf(turns.findActiveConversationIds(ids));
        Set<Long> idsWithTurns = Set.copyOf(turns.findConversationIdsWithTurns(ids));
        return rows.stream()
            .map(entity -> toConversation(
                entity,
                activeIds.contains(entity.getId()) || !idsWithTurns.contains(entity.getId())
                    ? ResumeConversationStatus.ACTIVE
                    : ResumeConversationStatus.FINISHED))
            .toList();
    }

    public List<ResumeTurnResponse> listTurns(Long accountId, Long conversationId) {
        requireConversation(accountId, conversationId);
        List<ResumeTurnEntity> turnRows = turns.listByConversation(conversationId);
        if (turnRows.isEmpty()) return List.of();
        Map<Long, List<ResumeAssistantMessageResponse>> byTurn = new LinkedHashMap<>();
        for (ResumeTurnEntity turn : turnRows) byTurn.put(turn.getId(), new ArrayList<>());
        for (ResumeAssistantMessageEntity message : messages.listByConversation(conversationId)) {
            byTurn.computeIfAbsent(message.getTurnId(), key -> new ArrayList<>())
                .add(toMessage(message));
        }
        return turnRows.stream()
            .map(turn -> toTurn(turn, byTurn.getOrDefault(turn.getId(), List.of())))
            .toList();
    }

    /**
     * Submits one user instruction. A running turn keeps the queue; the new turn
     * stays {@code queued} until the current one completes.
     */
    @Transactional
    public ResumeTurnResponse submitTurn(Long accountId, Long conversationId, String instruction) {
        requireConversation(accountId, conversationId);
        ResumeTurnEntity running = turns.findRunning(conversationId);
        ResumeTurnEntity turn = new ResumeTurnEntity();
        turn.setConversationId(conversationId);
        turn.setAccountId(accountId);
        turn.setInstruction(instruction);
        turn.setCreatedAt(LocalDateTime.now());
        turn.setQueuePosition(turns.maxQueuePosition(conversationId) + 1);
        if (running == null) {
            turn.setStatus(ResumeTurnStatus.RUNNING.wire());
            turn.setStartedAt(LocalDateTime.now());
        } else {
            turn.setStatus(ResumeTurnStatus.QUEUED.wire());
        }
        turns.insert(turn);
        return toTurn(turn, List.of());
    }

    /** Marks the running turn done and promotes the next queued turn, if any. */
    @Transactional
    public void completeTurn(Long accountId, Long turnId) {
        ResumeTurnEntity turn = turns.findOwned(turnId, accountId);
        if (turn == null) throw BusinessException.notFound("会话轮次不存在");
        turn.setStatus(ResumeTurnStatus.DONE.wire());
        turn.setCompletedAt(LocalDateTime.now());
        turns.updateById(turn);
        ResumeTurnEntity next = turns.findNextQueued(turn.getConversationId());
        if (next != null) {
            next.setStatus(ResumeTurnStatus.RUNNING.wire());
            next.setStartedAt(LocalDateTime.now());
            turns.updateById(next);
        }
        ResumeConversationEntity conversation = conversations.selectById(turn.getConversationId());
        if (conversation != null) {
            conversation.setUpdatedAt(LocalDateTime.now());
            conversations.updateById(conversation);
        }
    }

    /**
     * Controlled assistant step: one natural-language message and one tool-call
     * group, persisted and returned. Not a client-side mock — these rows are the
     * product truth the stream replays.
     */
    @Transactional
    public ResumeAssistantMessageResponse runAssistantStep(
        Long accountId,
        Long turnId,
        String messageContent,
        String summary,
        List<ToolCallDraft> toolDrafts
    ) {
        ResumeTurnEntity turn = turns.findOwned(turnId, accountId);
        if (turn == null) throw BusinessException.notFound("会话轮次不存在");
        ResumeAssistantMessageEntity message = new ResumeAssistantMessageEntity();
        message.setTurnId(turnId);
        message.setConversationId(turn.getConversationId());
        message.setSeqNum(messages.maxSeq(turnId) + 1);
        message.setContent(messageContent);
        message.setCreatedAt(LocalDateTime.now());
        messages.insert(message);

        if (!toolDrafts.isEmpty()) {
            ResumeToolGroupEntity group = new ResumeToolGroupEntity();
            group.setMessageId(message.getId());
            group.setLabel(summary);
            group.setStatus(ResumeToolState.DONE.wire());
            group.setCreatedAt(LocalDateTime.now());
            toolGroups.insert(group);
            int order = 0;
            for (ToolCallDraft draft : toolDrafts) {
                ResumeToolCallEntity call = new ResumeToolCallEntity();
                call.setGroupId(group.getId());
                call.setSortOrder(order++);
                call.setKind(draft.icon());
                call.setLabel(draft.text());
                call.setChip(String.join("\u0000", draft.chips() == null ? List.of() : draft.chips()));
                call.setDetail(writeDetail(draft.detail()));
                call.setState(ResumeToolState.DONE.wire());
                call.setError(draft.badge() == null ? null : draft.badge() + "|" + draft.badgeTone());
                toolCalls.insert(call);
            }
        }
        return toMessage(message);
    }

    public record ToolCallDraft(
        String icon,
        String text,
        String badge,
        String badgeTone,
        List<String> chips,
        List<String> detail
    ) {
    }

    private ResumeConversationEntity requireConversation(Long accountId, Long conversationId) {
        ResumeConversationEntity entity = conversations.findOwned(conversationId, accountId);
        if (entity == null) throw BusinessException.notFound("会话不存在");
        return entity;
    }

    private String writeDetail(List<String> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? List.of() : detail);
        } catch (Exception failure) {
            throw BusinessException.badRequest("工具明细序列化失败");
        }
    }

    private List<String> readDetail(String detail) {
        if (detail == null || detail.isBlank()) return List.of();
        try {
            return objectMapper.readValue(detail, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception failure) {
            return List.of();
        }
    }

    private ResumeConversationResponse toConversation(
        ResumeConversationEntity entity,
        ResumeConversationStatus status
    ) {
        return new ResumeConversationResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getResumeId(),
            entity.getUpdatedAt(),
            entity.getPinnedAt() != null,
            status.wire()
        );
    }

    /** Sidebar pin, same affordance the interview session list carries. */
    @Transactional
    public void pinConversation(Long accountId, Long conversationId, boolean pinned) {
        ResumeConversationEntity entity = requireConversation(accountId, conversationId);
        entity.setPinnedAt(pinned ? LocalDateTime.now() : null);
        conversations.updateById(entity);
    }

    /** Removes a conversation and its turns. The resume it is hung off stays in the library. */
    @Transactional
    public void deleteConversation(Long accountId, Long conversationId) {
        requireConversation(accountId, conversationId);
        conversations.deleteById(conversationId);
    }

    private ResumeTurnResponse toTurn(ResumeTurnEntity entity, List<ResumeAssistantMessageResponse> messageRows) {
        return new ResumeTurnResponse(
            entity.getId(),
            entity.getInstruction(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            messageRows
        );
    }

    private ResumeAssistantMessageResponse toMessage(ResumeAssistantMessageEntity entity) {
        ResumeToolGroupEntity group = toolGroups.findByMessage(entity.getId());
        return new ResumeAssistantMessageResponse(
            entity.getId(),
            entity.getTurnId(),
            entity.getContent(),
            entity.getCreatedAt(),
            group == null ? null : toToolGroup(group)
        );
    }

    private ResumeToolGroupResponse toToolGroup(ResumeToolGroupEntity group) {
        List<ResumeToolStepResponse> steps = toolCalls.listByGroup(group.getId()).stream()
            .map(call -> {
                String[] badgeParts = call.getError() == null ? new String[0] : call.getError().split("\\|", 2);
                return new ResumeToolStepResponse(
                    call.getId(),
                    call.getKind(),
                    call.getLabel(),
                    badgeParts.length > 0 ? badgeParts[0] : null,
                    badgeParts.length > 1 ? badgeParts[1] : null,
                    call.getChip() == null || call.getChip().isBlank()
                        ? List.of()
                        : List.of(call.getChip().split("\\u0000")),
                    readDetail(call.getDetail())
                );
            })
            .sorted(Comparator.comparing(ResumeToolStepResponse::id))
            .toList();
        return new ResumeToolGroupResponse(group.getId(), group.getLabel(), group.getStatus(), steps);
    }
}
