package com.prelude.resume.infrastructure;

import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeConversation;
import com.prelude.resume.domain.ResumeTurn;
import com.prelude.resume.infrastructure.persistence.ResumeConversationEntity;
import com.prelude.resume.infrastructure.persistence.ResumeConversationMapper;
import com.prelude.resume.infrastructure.persistence.ResumeTurnEntity;
import com.prelude.resume.infrastructure.persistence.ResumeTurnMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisResumeWorkspaceRepository implements ResumeWorkspaceRepository {

    private final ResumeConversationMapper conversations;
    private final ResumeTurnMapper turns;
    private final ResumeCodec codec;

    public MybatisResumeWorkspaceRepository(
        ResumeConversationMapper conversations,
        ResumeTurnMapper turns,
        ResumeCodec codec
    ) {
        this.conversations = conversations;
        this.turns = turns;
        this.codec = codec;
    }

    @Override
    public Long createConversation(ResumeConversation conversation) {
        ResumeConversationEntity row = new ResumeConversationEntity();
        row.setAccountId(conversation.accountId());
        row.setResumeId(conversation.resumeId());
        row.setTitle(conversation.title());
        row.setCreatedAt(conversation.createdAt());
        row.setUpdatedAt(conversation.updatedAt());
        conversations.insert(row);
        return row.getId();
    }

    @Override
    public Optional<ResumeConversation> findConversation(Long accountId, Long conversationId) {
        return Optional.ofNullable(conversations.findOwned(conversationId, accountId)).map(this::toConversation);
    }

    @Override
    public List<ResumeConversation> listConversations(Long accountId) {
        return conversations.listByOwner(accountId).stream().map(this::toConversation).toList();
    }

    @Override
    public void setPinned(Long accountId, Long conversationId, boolean pinned, LocalDateTime now) {
        conversations.updatePinnedAt(conversationId, accountId, pinned ? now : null, now);
    }

    @Override
    public void deleteConversation(Long accountId, Long conversationId) {
        conversations.deleteOwned(conversationId, accountId);
    }

    @Override
    public void touchConversation(Long conversationId, LocalDateTime now) {
        conversations.touch(conversationId, now);
    }

    /**
     * The queue slot is allocated from the conversation's current maximum, and the unique
     * key on {@code (conversation_id, queue_position)} is what makes two simultaneous
     * submissions pick different slots. Losing that race is expected under concurrency, so
     * it is retried once against the new maximum rather than reported to the candidate.
     */
    @Override
    public boolean attachResume(Long conversationId, Long resumeId) {
        return conversations.attachResume(conversationId, resumeId) == 1;
    }

    @Override
    public Long enqueueTurn(ResumeTurn turn) {
        try {
            return insertTurn(turn, turns.maxQueuePosition(turn.conversationId()) + 1);
        } catch (DuplicateKeyException first) {
            return insertTurn(turn, turns.maxQueuePosition(turn.conversationId()) + 1);
        }
    }

    private Long insertTurn(ResumeTurn turn, int queuePosition) {
        ResumeTurnEntity row = new ResumeTurnEntity();
        row.setConversationId(turn.conversationId());
        row.setAccountId(turn.accountId());
        row.setInstruction(turn.instruction());
        row.setBlockIds(codec.writeList(turn.blockIds()));
        row.setAttachmentIds(codec.writeList(turn.attachmentIds()));
        row.setStatus(turn.status().wire());
        row.setQueuePosition(queuePosition);
        row.setCreatedAt(turn.createdAt());
        turns.insert(row);
        return row.getId();
    }

    @Override
    public Optional<ResumeTurn> findTurn(Long accountId, Long turnId) {
        return Optional.ofNullable(turns.findOwned(turnId, accountId)).map(this::toTurn);
    }

    @Override
    public List<ResumeTurn> listTurns(Long conversationId) {
        return turns.listByConversation(conversationId).stream().map(this::toTurn).toList();
    }

    @Override
    public boolean claimTurn(Long turnId, LocalDateTime now) {
        return turns.claim(turnId, now) == 1;
    }

    @Override
    public boolean finishTurn(
        Long turnId,
        ResumeTurn.Status status,
        String failureReason,
        LocalDateTime now
    ) {
        return turns.finish(turnId, status.wire(), failureReason, now) == 1;
    }

    @Override
    public boolean cancelQueuedTurn(Long turnId, LocalDateTime now) {
        return turns.cancelQueued(turnId, now) == 1;
    }

    @Override
    public List<ResumeTurn> findRunnableTurns(int limit) {
        return turns.findRunnable(limit).stream().map(this::toTurn).toList();
    }

    @Override
    public List<ResumeTurn> findStaleRunningTurns(LocalDateTime cutoff, int limit) {
        return turns.findStaleRunning(cutoff, limit).stream().map(this::toTurn).toList();
    }

    @Override
    public Set<Long> conversationIdsWithPendingWork(Collection<Long> conversationIds) {
        return conversationIds.isEmpty()
            ? Set.of()
            : new HashSet<>(turns.findActiveConversationIds(conversationIds));
    }

    @Override
    public Set<Long> conversationIdsWithTurns(Collection<Long> conversationIds) {
        return conversationIds.isEmpty()
            ? Set.of()
            : new HashSet<>(turns.findConversationIdsWithTurns(conversationIds));
    }

    private ResumeConversation toConversation(ResumeConversationEntity row) {
        return new ResumeConversation(
            row.getId(),
            row.getAccountId(),
            row.getResumeId(),
            row.getTitle(),
            row.getPinnedAt(),
            row.getCreatedAt(),
            row.getUpdatedAt()
        );
    }

    private ResumeTurn toTurn(ResumeTurnEntity row) {
        return new ResumeTurn(
            row.getId(),
            row.getConversationId(),
            row.getAccountId(),
            row.getInstruction(),
            codec.readStringList(row.getBlockIds()),
            codec.readLongList(row.getAttachmentIds()),
            ResumeTurn.Status.fromWire(row.getStatus()),
            row.getQueuePosition(),
            row.getCreatedAt(),
            row.getStartedAt(),
            row.getCompletedAt(),
            row.getFailureReason()
        );
    }
}
