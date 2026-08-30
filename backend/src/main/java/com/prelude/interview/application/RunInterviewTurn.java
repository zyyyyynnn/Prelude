package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.LlmSelection;
import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.llm.ChatPort;
import com.prelude.llm.ChatRequest;
import com.prelude.llm.LlmPurpose;
import com.prelude.llm.LlmAttachment;
import com.prelude.llm.PromptIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RunInterviewTurn {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewMessageRepository interviewMessageRepository;
    private final ChatPort chatPort;
    private final InterviewStageManager interviewStageManager;
    private final InterviewContextService interviewContextService;
    private final InterviewMessageService interviewMessageService;
    private final AttachmentContextPort attachmentContextPort;

    public InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink) {
        InterviewMessage insertedUserMessage = null;
        boolean assistantPersisted = false;
        try {
            InterviewSession session = sessionAccess.requireOngoing(command.sessionId(), command.accountId());
            String content = normalizeContent(command.content());
            boolean firstRound = !hasConversationRound(command.sessionId());
            List<Map<String, String>> messages;

            if (command.autoStart() && firstRound && content.isEmpty()) {
                messages = interviewContextService.buildAutoStartMessages(session);
            } else {
                if (content.isEmpty()) {
                    throw BusinessException.badRequest("回答内容不能为空");
                }
                insertedUserMessage = interviewMessageService.insertMessage(command.sessionId(), ROLE_USER, content);
                sink.userAccepted(insertedUserMessage);
                messages = interviewContextService.buildContextMessages(command.sessionId());
            }

            StringBuilder assistantReply = new StringBuilder();
            List<LlmAttachment> imageAttachments = command.autoStart() && firstRound
                ? attachmentContextPort.list(session.getAccountId(), "interview", session.getId()).stream()
                    .filter(attachment -> attachment.image())
                    .map(attachment -> new LlmAttachment(
                        attachment.fileName(),
                        attachment.mediaType(),
                        attachmentContextPort.readOwnedContent(session.getAccountId(), attachment.assetRef())))
                    .toList()
                : List.of();
            streamAssistantReply(session, messages, imageAttachments, assistantReply, sink);
            boolean shouldAdvance = assistantReply.indexOf(STAGE_COMPLETE_TAG) >= 0;
            String finalReply = assistantReply.toString().replace(STAGE_COMPLETE_TAG, "").trim();
            if (!finalReply.isEmpty()) {
                interviewMessageService.insertMessage(command.sessionId(), ROLE_ASSISTANT, finalReply);
            }
            assistantPersisted = true;

            if (shouldAdvance) {
                interviewStageManager.advanceStage(command.sessionId(), command.completionPrompt());
            }
            return new InterviewTurnResult(session, insertedUserMessage, finalReply);
        } catch (RuntimeException error) {
            if (insertedUserMessage != null && insertedUserMessage.getId() != null && !assistantPersisted) {
                interviewMessageRepository.delete(insertedUserMessage.getId());
            }
            throw error;
        }
    }

    private boolean hasConversationRound(Long sessionId) {
        return interviewMessageRepository.countConversationMessages(sessionId) > 0;
    }

    private void streamAssistantReply(
        InterviewSession session,
        List<Map<String, String>> messages,
        List<LlmAttachment> attachments,
        StringBuilder assistantReply,
        InterviewTurnSink sink
    ) {
        chatPort.stream(
            ChatRequest.snapshot(
                session.getAccountId(),
                session.getId(),
                LlmPurpose.CHAT,
                PromptIds.CHAT,
                messages,
                new LlmSelection(session.getLlmProvider(), session.getLlmModel()),
                session.getLlmThinkingDepth() == null || session.getLlmThinkingDepth().isBlank()
                    ? null
                    : Map.of("thinking_depth", session.getLlmThinkingDepth()),
                attachments
            ),
            delta -> appendAndSend(assistantReply, sink, delta)
        );
    }

    private void appendAndSend(StringBuilder reply, InterviewTurnSink sink, String delta) {
        reply.append(delta);
        sink.assistantDelta(delta);
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }
}
