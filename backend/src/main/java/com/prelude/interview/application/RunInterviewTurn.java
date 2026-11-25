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
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.PromptIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RunInterviewTurn implements InterviewTurnPort {

    private static final String ROLE_USER = "user";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final LlmPort llmPort;
    private final InterviewContextPort interviewContextService;
    private final InterviewMessageService interviewMessageService;
    private final AttachmentContextPort attachmentContextPort;
    private final InterviewTurnCompletion interviewTurnCompletion;
    private final InterviewJudgeService interviewJudgeService;
    private final InterviewSummaryService interviewSummaryService;

    @Override
    public InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink) {
        InterviewSession session = sessionAccess.requireOngoing(command.sessionId(), command.accountId());
        String content = normalizeContent(command.content());
        boolean firstRound = !hasConversationRound(command.sessionId());
        InterviewMessage insertedUserMessage = null;
        List<Map<String, String>> messages;

        if (command.autoStart() && firstRound && content.isEmpty()) {
            messages = interviewContextService.buildAutoStartMessages(session);
        } else {
            if (content.isEmpty()) {
                throw BusinessException.badRequest("回答内容不能为空");
            }
            insertedUserMessage = interviewMessageService.insertMessage(command.sessionId(), ROLE_USER, content);
            sink.userAccepted(snapshotOf(insertedUserMessage));
            messages = interviewContextService.buildContextMessages(command.sessionId());
        }

        List<LlmPort.Attachment> imageAttachments = command.autoStart() && firstRound
            ? attachmentContextPort.list(session.getAccountId(), "interview", session.getId()).stream()
                .filter(attachment -> attachment.image())
                .map(attachment -> new LlmPort.Attachment(
                    attachment.fileName(),
                    attachment.mediaType(),
                    attachmentContextPort.readOwnedContent(session.getAccountId(), attachment.assetRef())))
                .toList()
            : List.of();
        StringBuilder assistantReply = new StringBuilder();
        streamAssistantReply(session, messages, imageAttachments, assistantReply, sink);
        String finalReply = assistantReply.toString().trim();
        interviewTurnCompletion.complete(command.sessionId(), finalReply);
        return new InterviewTurnResult(snapshotOf(session), snapshotOf(insertedUserMessage), finalReply);
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

    private boolean hasConversationRound(Long sessionId) {
        return interviewMessageRepository.countConversationMessages(sessionId) > 0;
    }

    private void streamAssistantReply(
        InterviewSession session,
        List<Map<String, String>> messages,
        List<LlmPort.Attachment> attachments,
        StringBuilder assistantReply,
        InterviewTurnSink sink
    ) {
        llmPort.stream(
            new LlmPort.ModelExecutionRequest(
                session.getModelExecutionSnapshotId(),
                "chat",
                PromptIds.CHAT,
                LlmPort.ResponseMode.PLAIN_TEXT,
                messages.stream()
                    .map(message -> new LlmPort.Message(message.get("role"), message.get("content")))
                    .toList(),
                attachments,
                List.of()
            ),
            new LlmPort.StreamSink() {
                @Override
                public void onNext(String delta) {
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
