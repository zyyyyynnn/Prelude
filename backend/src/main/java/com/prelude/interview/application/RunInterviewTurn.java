package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewContextPort;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.InterviewTurnSessionSnapshot;
import com.prelude.interview.application.port.InterviewUserTurnSnapshot;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.port.JudgeOutcome;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.PromptIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunInterviewTurn implements InterviewTurnPort {

    private static final Duration REPLY_RETRY_LEASE = Duration.ofMinutes(5);
    private static final RedisScript<Long> RELEASE_REPLY_LOCK = RedisScript.of(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);
    private static final RedisScript<Long> RENEW_REPLY_LOCK = RedisScript.of(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
        Long.class);
    private static final ScheduledExecutorService REPLY_LOCK_RENEWER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "interview-reply-lock-renewer");
        thread.setDaemon(true);
        return thread;
    });

    private final InterviewSessionAccess sessionAccess;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final ModelExecutionPort llmPort;
    private final InterviewContextPort interviewContextService;
    private final InterviewMessageService interviewMessageService;
    private final AttachmentContextPort attachmentContextPort;
    private final InterviewTurnCompletion interviewTurnCompletion;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink) {
        if (command.retryOfMessageId() != null && command.supersedesMessageId() != null) {
            throw BusinessException.badRequest("重试与取代回答不能同时指定");
        }
        InterviewSession session = sessionAccess.requireOngoing(command.sessionId(), command.accountId());
        String content = normalizeContent(command.content());
        boolean firstRound = !hasConversationRound(command.sessionId());
        InterviewMessage insertedUserMessage = null;
        List<Map<String, String>> messages;
        String replyLockKey = null;
        String replyLockToken = null;
        ScheduledFuture<?> replyLockRenewal = null;
        boolean replyLockAcquired = false;
        AtomicBoolean replyLockOwned = new AtomicBoolean(true);
        try {
            if (command.retryOfMessageId() != null) {
                InterviewMessage retryMessage = requireOwnedUserMessage(command.sessionId(), command.retryOfMessageId());
                if (retryMessage.getSupersededByMessageId() != null) {
                    throw BusinessException.badRequest("原回答已被其他回答取代");
                }
                InterviewMessage completed = interviewMessageRepository.findByReplyToMessageId(retryMessage.getId());
                if (completed != null) {
                    return completedReply(session, retryMessage, completed, sink);
                }
                insertedUserMessage = retryMessage;
                replyLockKey = replyLockKey(insertedUserMessage.getId());
                replyLockToken = UUID.randomUUID().toString();
                if (!acquireReplyLock(replyLockKey, replyLockToken)) {
                    return completedReplyAfterLockFailure(session, insertedUserMessage, sink);
                }
                replyLockAcquired = true;
                replyLockRenewal = startReplyLockRenewal(replyLockKey, replyLockToken, replyLockOwned);
                if (insertedUserMessage.getSupersededByMessageId() != null) {
                    throw BusinessException.badRequest("原回答已被其他回答取代");
                }
                InterviewMessage existingReply = interviewMessageRepository.findByReplyToMessageId(
                    insertedUserMessage.getId());
                if (existingReply != null) {
                    return completedReply(session, insertedUserMessage, existingReply, sink);
                }
                content = insertedUserMessage.getContent();
                messages = interviewContextService.buildContextMessages(command.sessionId());
            } else if (command.autoStart() && content.isEmpty()) {
                replyLockKey = "lock:interview-auto-start:" + command.sessionId();
                replyLockToken = UUID.randomUUID().toString();
                if (!acquireReplyLock(replyLockKey, replyLockToken)) {
                    InterviewMessage latest = interviewMessageRepository.findLatest(command.sessionId());
                    if (latest != null && "assistant".equals(latest.getRole())) {
                        return completedAutoStart(session, latest, sink);
                    }
                    throw BusinessException.badRequest("面试正在开始，请稍后重试");
                }
                replyLockAcquired = true;
                replyLockRenewal = startReplyLockRenewal(replyLockKey, replyLockToken, replyLockOwned);
                if (hasConversationRound(command.sessionId())) {
                    InterviewMessage latest = interviewMessageRepository.findLatest(command.sessionId());
                    if (latest != null && "assistant".equals(latest.getRole())) {
                        return completedAutoStart(session, latest, sink);
                    }
                    throw BusinessException.badRequest("面试正在开始，请稍后重试");
                }
                messages = interviewContextService.buildAutoStartMessages(session);
            } else {
                if (content.isEmpty()) {
                    throw BusinessException.badRequest("回答内容不能为空");
                }
                insertedUserMessage = interviewMessageService.insertUserMessage(
                    command.sessionId(), content, command.supersedesMessageId());
                replyLockKey = replyLockKey(insertedUserMessage.getId());
                replyLockToken = UUID.randomUUID().toString();
                if (!acquireReplyLock(replyLockKey, replyLockToken)) {
                    return completedReplyAfterLockFailure(session, insertedUserMessage, sink);
                }
                replyLockAcquired = true;
                replyLockRenewal = startReplyLockRenewal(replyLockKey, replyLockToken, replyLockOwned);
                sink.userAccepted(snapshotOf(insertedUserMessage));
                messages = interviewContextService.buildContextMessages(command.sessionId());
            }

            List<ModelExecutionPort.Attachment> imageAttachments = command.autoStart() && firstRound
                ? attachmentContextPort.list(session.getAccountId(), "interview", session.getId()).stream()
                    .filter(attachment -> attachment.image())
                    .map(attachment -> new ModelExecutionPort.Attachment(
                        attachment.fileName(),
                        attachment.mediaType(),
                        attachmentContextPort.readOwnedContent(session.getAccountId(), attachment.assetRef())))
                    .toList()
                : List.of();
            StringBuilder assistantReply = new StringBuilder();
            streamAssistantReply(session, messages, imageAttachments, assistantReply, replyLockOwned, sink);
            if (!replyLockOwned.get()) {
                throw BusinessException.badRequest("回答处理锁已失效，请重试");
            }
            String finalReply = assistantReply.toString().trim();
            interviewTurnCompletion.complete(
                command.sessionId(), insertedUserMessage == null ? null : insertedUserMessage.getId(), finalReply);
            return new InterviewTurnResult(snapshotOf(session), snapshotOf(insertedUserMessage), finalReply);
        } finally {
            if (replyLockRenewal != null) {
                replyLockRenewal.cancel(false);
            }
            if (replyLockAcquired) {
                stringRedisTemplate.execute(RELEASE_REPLY_LOCK, List.of(replyLockKey), replyLockToken);
            }
        }
    }

    private String replyLockKey(Long messageId) {
        return "lock:interview-reply:" + messageId;
    }

    private boolean acquireReplyLock(String lockKey, String lockToken) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
            lockKey, lockToken, REPLY_RETRY_LEASE));
    }

    private ScheduledFuture<?> startReplyLockRenewal(
        String lockKey, String lockToken, AtomicBoolean lockOwned
    ) {
        return REPLY_LOCK_RENEWER.scheduleAtFixedRate(
            () -> renewReplyLock(lockKey, lockToken, lockOwned), 1, 1, TimeUnit.MINUTES);
    }

    private InterviewTurnResult completedReplyAfterLockFailure(
        InterviewSession session, InterviewMessage userMessage, InterviewTurnSink sink
    ) {
        InterviewMessage completed = interviewMessageRepository.findByReplyToMessageId(userMessage.getId());
        if (completed != null) {
            return completedReply(session, userMessage, completed, sink);
        }
        throw BusinessException.badRequest("回答正在处理中，请稍后重试");
    }

    private InterviewTurnResult completedAutoStart(
        InterviewSession session, InterviewMessage completed, InterviewTurnSink sink
    ) {
        sink.assistantDelta(completed.getContent());
        return new InterviewTurnResult(snapshotOf(session), null, completed.getContent());
    }

    private InterviewTurnResult completedReply(
        InterviewSession session, InterviewMessage userMessage, InterviewMessage completed, InterviewTurnSink sink
    ) {
        sink.assistantDelta(completed.getContent());
        return new InterviewTurnResult(snapshotOf(session), snapshotOf(userMessage), completed.getContent());
    }

    private void renewReplyLock(String lockKey, String lockToken, AtomicBoolean lockOwned) {
        try {
            Long renewed = stringRedisTemplate.execute(
                RENEW_REPLY_LOCK, List.of(lockKey), lockToken, String.valueOf(REPLY_RETRY_LEASE.toMillis()));
            if (Long.valueOf(0).equals(renewed)) {
                lockOwned.set(false);
                log.warn("Interview reply lock was lost for {}", lockKey);
            }
        } catch (RuntimeException exception) {
            lockOwned.set(false);
            log.warn("Could not renew interview reply lock {}", lockKey, exception);
        }
    }

    @Override
    public JudgeOutcome judgeAndPersist(Long sessionId, Long messageId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        InterviewMessage userMessage = interviewMessageRepository.findById(messageId);
        if (session == null || userMessage == null) {
            throw BusinessException.notFound("面试消息不存在");
        }
        return interviewJudgeService.judgeAndPersist(session, userMessage);
    }

    @Override
    public void summarizeIfNeeded(Long sessionId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        if (session != null) {
            interviewSummaryService.triggerAsyncSummarizeIfNeeded(session);
        }
    }

    private InterviewTurnSessionSnapshot snapshotOf(InterviewSession session) {
        return new InterviewTurnSessionSnapshot(
            session.getId(),
            session.getAccountId(),
            session.getTargetPosition(),
            session.getModelExecutionSnapshotId()
        );
    }

    private InterviewUserTurnSnapshot snapshotOf(InterviewMessage message) {
        if (message == null) {
            return null;
        }
        return new InterviewUserTurnSnapshot(
            message.getId(),
            message.getSessionId(),
            message.getContent(),
            message.getSeqNum(),
            message.getCreatedAt()
        );
    }

    private InterviewMessage requireOwnedUserMessage(Long sessionId, Long messageId) {
        InterviewMessage message = interviewMessageRepository.findById(messageId);
        if (message == null || !sessionId.equals(message.getSessionId()) || !"user".equals(message.getRole())) {
            throw BusinessException.badRequest("要重试的回答不存在");
        }
        return message;
    }

    private boolean hasConversationRound(Long sessionId) {
        return interviewMessageRepository.countConversationMessages(sessionId) > 0;
    }

    private void streamAssistantReply(
        InterviewSession session,
        List<Map<String, String>> messages,
        List<ModelExecutionPort.Attachment> attachments,
        StringBuilder assistantReply,
        AtomicBoolean lockOwned,
        InterviewTurnSink sink
    ) {
        llmPort.stream(
            new ModelExecutionPort.ModelExecutionRequest(
                session.getModelExecutionSnapshotId(),
                "chat",
                PromptIds.CHAT,
                ModelExecutionPort.ResponseMode.PLAIN_TEXT,
                messages.stream()
                    .map(message -> new ModelExecutionPort.Message(message.get("role"), message.get("content")))
                    .toList(),
                attachments,
                List.of()
            ),
            new ModelExecutionPort.StreamSink() {
                @Override
                public void onNext(String delta) {
                    if (!lockOwned.get()) {
                        throw BusinessException.badRequest("回答处理锁已失效，请重试");
                    }
                    assistantReply.append(delta);
                    sink.assistantDelta(delta);
                }
            }
        );
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }
}
